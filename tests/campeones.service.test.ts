import { prisma } from '../src/lib/prisma';
import { crearCampeon, obtenerMaximosCampeones } from '../src/modules/campeones/campeones.service';
import { crearCampeonSchema } from '../src/modules/campeones/campeones.validation';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    categoria: { findUnique: jest.fn() },
    equipo: { findMany: jest.fn(), findUnique: jest.fn() },
    campeon: { create: jest.fn(), findMany: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  categoria: { findUnique: jest.Mock };
  equipo: { findMany: jest.Mock; findUnique: jest.Mock };
  campeon: { create: jest.Mock; findMany: jest.Mock };
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('crearCampeon', () => {
  it('rechaza si la categoría no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(crearCampeon({ anio: 2026, categoriaId: 1, equipoId: 1, instancia: 'APERTURA' })).rejects.toThrow(
      NotFoundError,
    );
  });

  it('rechaza si el equipo no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 1 });
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(crearCampeon({ anio: 2026, categoriaId: 1, equipoId: 1, instancia: 'APERTURA' })).rejects.toThrow(
      NotFoundError,
    );
  });

  it('crea el campeón si categoría y equipo existen', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 1 });
    mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 1 });
    mockedPrisma.campeon.create.mockResolvedValue({ id: 1, anio: 2026, categoriaId: 1, equipoId: 1 });

    const resultado = await crearCampeon({ anio: 2026, categoriaId: 1, equipoId: 1, instancia: 'FINAL_ANUAL' });
    expect(resultado.anio).toBe(2026);
    expect(mockedPrisma.campeon.create).toHaveBeenCalledWith({
      data: { anio: 2026, categoriaId: 1, equipoId: 1, instancia: 'FINAL_ANUAL' },
    });
  });
});

describe('obtenerMaximosCampeones', () => {
  it('cuenta años distintos y ordena de mayor a menor', async () => {
    mockedPrisma.campeon.findMany.mockResolvedValue([
      { anio: 2024, categoriaId: 1, equipoId: 1 },
      { anio: 2025, categoriaId: 1, equipoId: 1 },
      { anio: 2024, categoriaId: 1, equipoId: 2 },
      { anio: 2025, categoriaId: 1, equipoId: 2 },
      { anio: 2025, categoriaId: 2, equipoId: 2 },
    ]);
    mockedPrisma.equipo.findMany.mockResolvedValue([
      { id: 1, nombre: 'GULP' },
      { id: 2, nombre: 'BOCHA FC' },
    ]);

    const resultado = await obtenerMaximosCampeones();

    expect(resultado).toEqual([
      { equipoId: 2, nombre: 'BOCHA FC', titulos: 3 },
      { equipoId: 1, nombre: 'GULP', titulos: 2 },
    ]);
  });

  it('un año con Apertura + Final cuenta una sola vez (campeón una vez, 2 filas)', async () => {
    mockedPrisma.campeon.findMany.mockResolvedValue([
      { anio: 2024, categoriaId: 1, equipoId: 1, instancia: 'APERTURA' },
      { anio: 2024, categoriaId: 1, equipoId: 1, instancia: 'FINAL_ANUAL' },
      { anio: 2024, categoriaId: 1, equipoId: 2, instancia: 'CLAUSURA' },
    ]);
    mockedPrisma.equipo.findMany.mockResolvedValue([
      { id: 1, nombre: 'GULP' },
      { id: 2, nombre: 'BOCHA FC' },
    ]);

    const resultado = await obtenerMaximosCampeones();

    expect(resultado).toEqual([
      { equipoId: 2, nombre: 'BOCHA FC', titulos: 1 },
      { equipoId: 1, nombre: 'GULP', titulos: 1 },
    ]);
  });

  it('filtra por categoría cuando viene', async () => {
    mockedPrisma.campeon.findMany.mockResolvedValue([]);
    await obtenerMaximosCampeones(1);
    expect(mockedPrisma.campeon.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { categoriaId: 1 } }),
    );
  });

  it('devuelve array vacío si no hay campeones cargados', async () => {
    mockedPrisma.campeon.findMany.mockResolvedValue([]);
    const resultado = await obtenerMaximosCampeones();
    expect(resultado).toEqual([]);
    expect(mockedPrisma.equipo.findMany).not.toHaveBeenCalled();
  });
});

describe('crearCampeonSchema (validación)', () => {
  it('exige instancia', () => {
    expect(crearCampeonSchema.safeParse({ anio: 2026, categoriaId: 1, equipoId: 1 }).success).toBe(false);
  });

  it('rechaza DESCONOCIDO en carga manual (solo para historia importada)', () => {
    const r = crearCampeonSchema.safeParse({ anio: 2026, categoriaId: 1, equipoId: 1, instancia: 'DESCONOCIDO' });
    expect(r.success).toBe(false);
  });

  it.each(['APERTURA', 'CLAUSURA', 'FINAL_ANUAL'] as const)('acepta instancia %s', (instancia) => {
    const r = crearCampeonSchema.safeParse({ anio: 2026, categoriaId: 1, equipoId: 1, instancia });
    expect(r.success).toBe(true);
  });
});
