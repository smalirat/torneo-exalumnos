import * as XLSX from 'xlsx';
import { prisma } from '../src/lib/prisma';
import { importarHistoria } from '../src/modules/excel/imports/historiaImport.service';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    temporada: { findUnique: jest.fn(), create: jest.fn() },
    torneo: { findFirst: jest.fn(), create: jest.fn() },
    categoria: { findFirst: jest.fn(), create: jest.fn() },
    equipo: { findFirst: jest.fn(), create: jest.fn() },
    campeon: { findFirst: jest.fn(), create: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  temporada: { findUnique: jest.Mock; create: jest.Mock };
  torneo: { findFirst: jest.Mock; create: jest.Mock };
  categoria: { findFirst: jest.Mock; create: jest.Mock };
  equipo: { findFirst: jest.Mock; create: jest.Mock };
  campeon: { findFirst: jest.Mock; create: jest.Mock };
};

type Celda = string | number | null;

function libroHistoria(hoja: Celda[][]): Buffer {
  const wb = XLSX.utils.book_new();
  XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(hoja), 'HISTORIA');
  return XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
}

const HOJA = [
  ['LISTADO DE CAMPEONES'],
  ['Categoria "A"', null, null, null, null, null, null, null, 'Categoria "B"'],
  ['Año', 'Equipo Campeon', null, null, null, null, null, null, 'Año', 'Equipo Campeon'],
  [2024, 'TARRE', null, null, null, null, null, null, 2024, 'GULP'],
  [2026, 'Proximamente!!', null, null, null, null, null, null, 2020, null],
] as Celda[][];
beforeEach(() => {
  jest.clearAllMocks();
  let nextId = 1;
  const crear = ({ data }: { data: Record<string, unknown> }) => Promise.resolve({ id: nextId++, ...data });

  // Stores con estado: lo creado por una fila lo ve la siguiente (como la DB real)
  const temporadas = new Map<number, { id: number; anio: number }>();
  const torneos = new Map<string, { id: number }>();
  const categorias = new Map<string, { id: number }>();
  mockedPrisma.temporada.findUnique.mockImplementation(({ where }: { where: { anio: number } }) =>
    Promise.resolve(temporadas.get(where.anio) ?? null),
  );
  mockedPrisma.temporada.create.mockImplementation(({ data }: { data: { anio: number } }) => {
    const t = { id: nextId++, ...data };
    temporadas.set(data.anio, t);
    return Promise.resolve(t);
  });
  mockedPrisma.torneo.findFirst.mockImplementation(({ where }: { where: { temporadaId: number; nombre: string } }) =>
    Promise.resolve(torneos.get(`${where.temporadaId}-${where.nombre}`) ?? null),
  );
  mockedPrisma.torneo.create.mockImplementation(
    ({ data }: { data: { temporadaId: number; nombre: string } }) => {
      const t = { id: nextId++ };
      torneos.set(`${data.temporadaId}-${data.nombre}`, t);
      return Promise.resolve(t);
    },
  );
  mockedPrisma.categoria.findFirst.mockImplementation(
    ({ where }: { where: { torneoId: number; nombre: string } }) =>
      Promise.resolve(categorias.get(`${where.torneoId}-${where.nombre}`) ?? null),
  );
  mockedPrisma.categoria.create.mockImplementation(
    ({ data }: { data: { torneoId: number; nombre: string } }) => {
      const c = { id: nextId++ };
      categorias.set(`${data.torneoId}-${data.nombre}`, c);
      return Promise.resolve(c);
    },
  );
  mockedPrisma.equipo.findFirst.mockResolvedValue(null);
  mockedPrisma.equipo.create.mockImplementation(crear);
  mockedPrisma.campeon.findFirst.mockResolvedValue(null);
  mockedPrisma.campeon.create.mockImplementation(crear);
});

describe('importarHistoria', () => {
  it('crea la cadena Temporada -> Torneo(APERTURA) -> Categoria -> Equipo -> Campeon', async () => {
    const resumen = await importarHistoria(libroHistoria(HOJA));
    expect(resumen.campeonesCreados).toBe(2);
    expect(resumen.temporadasCreadas).toBe(1);
    expect(resumen.torneosCreados).toBe(1);
    expect(resumen.categoriasCreadas).toBe(2);
    expect(resumen.equiposCreados).toBe(2);
    expect(mockedPrisma.temporada.create).toHaveBeenCalledWith({ data: { anio: 2024 } });
    expect(mockedPrisma.torneo.create).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ nombre: 'APERTURA' }) }),
    );
    expect(mockedPrisma.campeon.create).toHaveBeenCalledWith({
      data: { anio: 2024, categoriaId: expect.any(Number), equipoId: expect.any(Number) },
    });
    // Placeholders reportados como omitidos, sin writes
    expect(resumen.omitidos).toContainEqual(
      expect.objectContaining({ anio: 2026, categoria: 'A', motivo: 'PLACEHOLDER' }),
    );
    expect(resumen.omitidos).toContainEqual(
      expect.objectContaining({ anio: 2020, categoria: 'B', motivo: 'SIN_CAMPEON' }),
    );
  });

  it('reutiliza lo existente y no duplica campeones (YA_CARGADO)', async () => {
    mockedPrisma.temporada.findUnique.mockResolvedValue({ id: 9, anio: 2024 });
    mockedPrisma.torneo.findFirst.mockResolvedValue({ id: 5 });
    mockedPrisma.categoria.findFirst.mockResolvedValue({ id: 10 });
    mockedPrisma.equipo.findFirst.mockResolvedValue({ id: 1, nombre: 'TARRE' });
    mockedPrisma.campeon.findFirst.mockResolvedValue({ id: 7 });
    const resumen = await importarHistoria(libroHistoria(HOJA));
    expect(resumen.campeonesCreados).toBe(0);
    expect(mockedPrisma.temporada.create).not.toHaveBeenCalled();
    expect(mockedPrisma.campeon.create).not.toHaveBeenCalled();
    expect(resumen.omitidos.filter((o) => o.motivo === 'YA_CARGADO')).toHaveLength(2);
  });

  it('sin hoja HISTORIA devuelve advertencia y no escribe nada', async () => {
    const wb = XLSX.utils.book_new();
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet([['nada']]), 'Otra');
    const resumen = await importarHistoria(XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' }));
    expect(resumen.advertencias).toHaveLength(1);
    expect(resumen.campeonesCreados).toBe(0);
    expect(mockedPrisma.temporada.create).not.toHaveBeenCalled();
  });
});
