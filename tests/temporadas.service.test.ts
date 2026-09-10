import { prisma } from '../src/lib/prisma';
import {
  actualizarTemporada,
  crearTemporada,
  eliminarTemporada,
  listarTemporadas,
  obtenerTemporada,
} from '../src/modules/temporadas/temporadas.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    temporada: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  temporada: { findMany: jest.Mock; findUnique: jest.Mock; create: jest.Mock; update: jest.Mock; delete: jest.Mock };
};

const temporadaMock = { id: 9, anio: 2026 };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.temporada.findUnique.mockResolvedValue(temporadaMock);
  mockedPrisma.temporada.create.mockImplementation(({ data }) => Promise.resolve({ id: 9, ...data }));
});

describe('listarTemporadas / obtenerTemporada', () => {
  it('lista ordenada por año descendente', async () => {
    await listarTemporadas();
    expect(mockedPrisma.temporada.findMany).toHaveBeenCalledWith({ orderBy: { anio: 'desc' } });
  });

  it('obtiene por id', async () => {
    await expect(obtenerTemporada(9)).resolves.toEqual(temporadaMock);
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.temporada.findUnique.mockResolvedValue(null);
    await expect(obtenerTemporada(999)).rejects.toThrow(NotFoundError);
  });
});

describe('crearTemporada / actualizarTemporada / eliminarTemporada', () => {
  it('crea con el año', async () => {
    await expect(crearTemporada({ anio: 2026 })).resolves.toMatchObject({ anio: 2026 });
  });

  it('actualiza y elimina tras verificar que existe', async () => {
    await actualizarTemporada(9, { anio: 2027 });
    expect(mockedPrisma.temporada.update).toHaveBeenCalledWith({ where: { id: 9 }, data: { anio: 2027 } });
    await eliminarTemporada(9);
    expect(mockedPrisma.temporada.delete).toHaveBeenCalledWith({ where: { id: 9 } });
  });

  it('no actualiza ni elimina si no existe', async () => {
    mockedPrisma.temporada.findUnique.mockResolvedValue(null);
    await expect(actualizarTemporada(999, { anio: 2027 })).rejects.toThrow(NotFoundError);
    await expect(eliminarTemporada(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.temporada.update).not.toHaveBeenCalled();
    expect(mockedPrisma.temporada.delete).not.toHaveBeenCalled();
  });
});
