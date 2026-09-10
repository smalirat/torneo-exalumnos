import { EstadoImportacion } from '@prisma/client';
import * as XLSX from 'xlsx';
import { prisma } from '../src/lib/prisma';
import { importarExcel } from '../src/modules/excel/imports/excelImport.service';
import { NotFoundError } from '../src/utils/AppError';

// El importador habla con Prisma en muchos modelos distintos, así que el
// mock es ancho pero playo: cada delegado es un jest.fn() y los defaults
// "felices" se setean en beforeEach. Lo que SÍ es real es el Excel: armamos
// workbooks xlsx en memoria para ejercitar el pipeline completo
// (leerLibro -> buscarHoja -> parsers -> helpers -> resumen).
jest.mock('../src/lib/prisma', () => ({
  prisma: {
    torneo: { findUnique: jest.fn(), findFirst: jest.fn() },
    importacionExcel: { create: jest.fn(), update: jest.fn() },
    categoria: { findMany: jest.fn() },
    equipo: { findFirst: jest.fn(), create: jest.fn() },
    jugador: { findFirst: jest.fn(), create: jest.fn() },
    inscripcionEquipo: { findFirst: jest.fn(), create: jest.fn() },
    puntosPresentismo: { deleteMany: jest.fn(), create: jest.fn() },
    partido: { findFirst: jest.fn(), create: jest.fn(), update: jest.fn() },
    goleador: { upsert: jest.fn() },
    figura: { upsert: jest.fn() },
    imbatible: { upsert: jest.fn() },
    amonestacion: { upsert: jest.fn() },
    sancion: { findFirst: jest.fn(), create: jest.fn(), update: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  torneo: { findUnique: jest.Mock; findFirst: jest.Mock };
  importacionExcel: { create: jest.Mock; update: jest.Mock };
  categoria: { findMany: jest.Mock };
  equipo: { findFirst: jest.Mock; create: jest.Mock };
  jugador: { findFirst: jest.Mock; create: jest.Mock };
  inscripcionEquipo: { findFirst: jest.Mock; create: jest.Mock };
  puntosPresentismo: { deleteMany: jest.Mock; create: jest.Mock };
  partido: { findFirst: jest.Mock; create: jest.Mock; update: jest.Mock };
  goleador: { upsert: jest.Mock };
  figura: { upsert: jest.Mock };
  imbatible: { upsert: jest.Mock };
  amonestacion: { upsert: jest.Mock };
  sancion: { findFirst: jest.Mock; create: jest.Mock; update: jest.Mock };
};

type Celda = string | number | null;

function libroConHojas(hojas: Record<string, Celda[][]>): Buffer {
  const wb = XLSX.utils.book_new();
  for (const [nombre, aoa] of Object.entries(hojas)) {
    XLSX.utils.book_append_sheet(wb, XLSX.utils.aoa_to_sheet(aoa), nombre);
  }
  return XLSX.write(wb, { type: 'buffer', bookType: 'xlsx' });
}

const archivo = (buffer: Buffer) => ({ buffer, nombreOriginal: 'test.xlsx', rutaGuardada: '/tmp/test.xlsx' });

const bloqueFixture = (label: Celda[], filas: Celda[][]): Celda[][] => [
  label,
  [null],
  ['Campo', 'Cancha', 'Horario', 'Equipo', null, 'vs', 'Equipo', null, 'Categoría', null],
  [null],
  ...filas,
];

// Estructura mínima para que resolverCategoriaZona('ZONA 1') resuelva sin
// ambigüedad: una sola categoría con una sola "Zona N".
const categoriasMock = [{ id: 10, torneoId: 1, nombre: 'A', zonas: [{ id: 20, nombre: 'Zona 1', categoriaId: 10 }] }];

beforeEach(() => {
  jest.clearAllMocks();
  let nextId = 1;
  const crear = ({ data }: { data: Record<string, unknown> }) => Promise.resolve({ id: nextId++, ...data });

  mockedPrisma.torneo.findUnique.mockResolvedValue({
    id: 1,
    nombre: 'APERTURA',
    temporadaId: 9,
    temporada: { id: 9, anio: 2026 },
  });
  mockedPrisma.importacionExcel.create.mockResolvedValue({ id: 100 });
  mockedPrisma.importacionExcel.update.mockResolvedValue({});
  mockedPrisma.categoria.findMany.mockResolvedValue(categoriasMock);
  mockedPrisma.equipo.findFirst.mockResolvedValue(null);
  mockedPrisma.equipo.create.mockImplementation(crear);
  mockedPrisma.jugador.findFirst.mockResolvedValue(null);
  mockedPrisma.jugador.create.mockImplementation(crear);
  mockedPrisma.inscripcionEquipo.findFirst.mockResolvedValue(null);
  mockedPrisma.inscripcionEquipo.create.mockImplementation(crear);
  mockedPrisma.puntosPresentismo.deleteMany.mockResolvedValue({ count: 0 });
  mockedPrisma.puntosPresentismo.create.mockImplementation(crear);
  mockedPrisma.partido.findFirst.mockResolvedValue(null);
  mockedPrisma.partido.create.mockImplementation(crear);
  mockedPrisma.partido.update.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));
  mockedPrisma.goleador.upsert.mockResolvedValue({});
  mockedPrisma.figura.upsert.mockResolvedValue({});
  mockedPrisma.imbatible.upsert.mockResolvedValue({});
  mockedPrisma.amonestacion.upsert.mockResolvedValue({});
  mockedPrisma.sancion.findFirst.mockResolvedValue(null);
  mockedPrisma.sancion.create.mockImplementation(crear);
  mockedPrisma.sancion.update.mockResolvedValue({});
});

describe('importarExcel', () => {
  it('rechaza si el torneo no existe (sin crear registro de importación)', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(importarExcel(999, archivo(libroConHojas({ Notas: [['hola']] })))).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.importacionExcel.create).not.toHaveBeenCalled();
  });

  it('libro sin hojas conocidas: COMPLETADO con advertencias y conteos en cero', async () => {
    const resumen = await importarExcel(1, archivo(libroConHojas({ Notas: [['nada útil acá']] })));
    expect(resumen.advertencias).toHaveLength(6);
    expect(resumen).toMatchObject({
      equiposCreados: 0,
      partidosCreados: 0,
      sancionesCreadas: 0,
      amonestadosActualizados: 0,
    });
    expect(mockedPrisma.importacionExcel.update).toHaveBeenCalledWith({
      where: { id: 100 },
      data: expect.objectContaining({ estado: EstadoImportacion.COMPLETADO }),
    });
  });

  it('fixture: crea los dos equipos y un partido PENDIENTE con fecha calendario', async () => {
    const buffer = libroConHojas({
      'Prox Partido': bloqueFixture(
        ['FECHA Nº 1', null, null, null, null, null, 'Domingo 29 de Marzo', null, null, null],
        [['Siberia', 3, '9:00 hs', 'GULP', null, 'vs', 'BOCHA FC', null, 'Zona 1', null]],
      ),
    });
    const resumen = await importarExcel(1, archivo(buffer));
    expect(resumen.partidosCreados).toBe(1);
    expect(resumen.equiposCreados).toBe(2);
    expect(mockedPrisma.partido.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        categoriaId: 10,
        zonaId: 20,
        jornada: 1,
        estado: 'PENDIENTE',
        fecha: new Date(2026, 2, 29),
      }),
    });
  });

  it('fixture: un partido ya existente se actualiza en vez de duplicarse', async () => {
    mockedPrisma.partido.findFirst.mockResolvedValue({ id: 55 });
    const buffer = libroConHojas({
      'Prox Partido': bloqueFixture(
        ['FECHA Nº 1', null, null, null, null, null, 'Domingo 29 de Marzo', null, null, null],
        [['Siberia', 3, '9:00 hs', 'GULP', 2, 'vs', 'BOCHA FC', 1, 'Zona 1', null]],
      ),
    });
    const resumen = await importarExcel(1, archivo(buffer));
    expect(resumen.partidosCreados).toBe(0);
    expect(resumen.partidosActualizados).toBe(1);
    expect(mockedPrisma.partido.update).toHaveBeenCalledWith({
      where: { id: 55 },
      data: expect.objectContaining({ golesLocal: 2, golesVisitante: 1, estado: 'JUGADO' }),
    });
  });

  it('tabla: inscribe equipos y carga PR reemplazando los anteriores (idempotente)', async () => {
    const buffer = libroConHojas({
      'Tabla Apertura': [['ZONA 1'], ['EQUIPO', 'PR'], ['GULP', 2], ['BOCHA FC', 0]],
    });
    const resumen = await importarExcel(1, archivo(buffer));
    expect(mockedPrisma.puntosPresentismo.deleteMany).toHaveBeenCalledWith({
      where: { categoriaId: 10, zonaId: 20 },
    });
    expect(resumen.puntosPresentismoCargados).toBe(1);
    expect(mockedPrisma.puntosPresentismo.create).toHaveBeenCalledTimes(1);
    expect(mockedPrisma.puntosPresentismo.create).toHaveBeenCalledWith({
      data: expect.objectContaining({ categoriaId: 10, zonaId: 20, puntos: 2 }),
    });
    expect(mockedPrisma.inscripcionEquipo.create).toHaveBeenCalledTimes(2);
  });

  it('sancionados: crea Sancion PENDIENTE y hace upsert de Amonestacion', async () => {
    const buffer = libroConHojas({
      Sancionados: [
        ['EXPULSADOS'],
        ['JUGADOR', 'EQUIPO', 'FECHAS'],
        ['Juan Pérez', 'GULP', 2],
        ['AMONESTADOS'],
        ['JUGADOR', 'EQUIPO', 'AMARILLAS'],
        ['Pedro Gómez', 'BOCHA FC', 4],
      ],
    });
    const resumen = await importarExcel(1, archivo(buffer));
    expect(resumen.sancionesCreadas).toBe(1);
    expect(mockedPrisma.sancion.create).toHaveBeenCalledWith({
      data: expect.objectContaining({
        torneoId: 1,
        tipoTarjeta: 'ROJA',
        fechasSuspension: 2,
        estado: 'PENDIENTE',
      }),
    });
    expect(resumen.amonestadosActualizados).toBe(1);
    expect(mockedPrisma.amonestacion.upsert).toHaveBeenCalledWith(
      expect.objectContaining({
        create: expect.objectContaining({ torneoId: 1, cantidad: 4 }),
        update: expect.objectContaining({ cantidad: 4 }),
      }),
    );
    expect(resumen.jugadoresCreados).toBe(2);
  });

  it('sancion existente con mismo estado no se toca (solo crea la faltante)', async () => {
    mockedPrisma.sancion.findFirst.mockResolvedValue({ id: 9, estado: 'PENDIENTE', fechasCumplidas: 0 });
    const buffer = libroConHojas({
      Sancionados: [['EXPULSADOS'], ['JUGADOR', 'EQUIPO', 'FECHAS'], ['Juan Pérez', 'GULP', 2]],
    });
    const resumen = await importarExcel(1, archivo(buffer));
    expect(resumen.sancionesCreadas).toBe(0);
    expect(mockedPrisma.sancion.create).not.toHaveBeenCalled();
    expect(mockedPrisma.sancion.update).not.toHaveBeenCalled();
  });

  it('goleadores: resuelve equipo/jugador y fija el ranking', async () => {
    const buffer = libroConHojas({
      Goleadores: [
        ['JUGADOR', 'EQUIPO', 'GOLES'],
        ['Juan Pérez', 'GULP', 8],
      ],
    });
    const resumen = await importarExcel(1, archivo(buffer));
    expect(resumen.goleadoresActualizados).toBe(1);
    expect(mockedPrisma.goleador.upsert).toHaveBeenCalledWith(
      expect.objectContaining({ create: expect.objectContaining({ torneoId: 1, goles: 8 }) }),
    );
  });

  it('buffer inválido: marca la importación como ERROR y relanza', async () => {
    await expect(importarExcel(1, archivo(null as unknown as Buffer))).rejects.toThrow();
    expect(mockedPrisma.importacionExcel.update).toHaveBeenCalledWith({
      where: { id: 100 },
      data: expect.objectContaining({ estado: EstadoImportacion.ERROR }),
    });
  });
});
