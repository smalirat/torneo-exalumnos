import { prisma } from '../../src/lib/prisma';
import { crearCampeon, obtenerMaximosCampeones } from '../../src/modules/campeones/campeones.service';
import { NotFoundError } from '../../src/utils/AppError';

jest.mock('../../src/lib/prisma', () => ({
  prisma: {
    categoria: { findUnique: jest.fn() },
    equipo: { findMany: jest.fn(), findUnique: jest.fn() },
    campeon: { create: jest.fn(), groupBy: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  categoria: { findUnique: jest.Mock };
  equipo: { findMany: jest.Mock; findUnique: jest.Mock };
  campeon: { create: jest.Mock; groupBy: jest.Mock };
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('crearCampeon', () => {
  it('rechaza si la categoría no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(crearCampeon({ anio: 2026, categoriaId: 1, equipoId: 1 })).rejects.toThrow(NotFoundError);
  });

  it('rechaza si el equipo no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 1 });
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(crearCampeon({ anio: 2026, categoriaId: 1, equipoId: 1 })).rejects.toThrow(NotFoundError);
  });

  it('crea el campeón si categoría y equipo existen', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 1 });
    mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 1 });
    mockedPrisma.campeon.create.mockResolvedValue({ id: 1, anio: 2026, categoriaId: 1, equipoId: 1 });

    const resultado = await crearCampeon({ anio: 2026, categoriaId: 1, equipoId: 1 });
    expect(resultado.anio).toBe(2026);
  });
});

describe('obtenerMaximosCampeones', () => {
  it('agrupa por equipo, cuenta títulos y ordena de mayor a menor', async () => {
    mockedPrisma.campeon.groupBy.mockResolvedValue([
      { equipoId: 1, _count: { equipoId: 2 } },
      { equipoId: 2, _count: { equipoId: 5 } },
    ]);
    mockedPrisma.equipo.findMany.mockResolvedValue([
      { id: 1, nombre: 'GULP' },
      { id: 2, nombre: 'BOCHA FC' },
    ]);

    const resultado = await obtenerMaximosCampeones();

    expect(resultado).toEqual([
      { equipoId: 2, nombre: 'BOCHA FC', titulos: 5 },
      { equipoId: 1, nombre: 'GULP', titulos: 2 },
    ]);
  });

  it('devuelve array vacío si no hay campeones cargados', async () => {
    mockedPrisma.campeon.groupBy.mockResolvedValue([]);
    const resultado = await obtenerMaximosCampeones();
    expect(resultado).toEqual([]);
    expect(mockedPrisma.equipo.findMany).not.toHaveBeenCalled();
  });
});
