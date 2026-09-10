import { prisma } from '../src/lib/prisma';
import {
  actualizarTorneo,
  crearTorneo,
  eliminarTorneo,
  listarTorneos,
  obtenerTorneo,
} from '../src/modules/torneos/torneos.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    temporada: { findUnique: jest.fn() },
    torneo: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  temporada: { findUnique: jest.Mock };
  torneo: { findMany: jest.Mock; findUnique: jest.Mock; create: jest.Mock; update: jest.Mock; delete: jest.Mock };
};

const torneoMock = { id: 1, temporadaId: 9, nombre: 'APERTURA' };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.torneo.findUnique.mockResolvedValue(torneoMock);
  mockedPrisma.temporada.findUnique.mockResolvedValue({ id: 9, anio: 2026 });
  mockedPrisma.torneo.create.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));
});

describe('listarTorneos / obtenerTorneo', () => {
  it('lista con filtro opcional incluyendo temporada', async () => {
    await listarTorneos(9);
    expect(mockedPrisma.torneo.findMany).toHaveBeenCalledWith({
      where: { temporadaId: 9 },
      include: { temporada: true },
      orderBy: [{ temporadaId: 'desc' }, { nombre: 'asc' }],
    });
  });

  it('obtiene por id', async () => {
    await expect(obtenerTorneo(1)).resolves.toEqual(torneoMock);
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(obtenerTorneo(999)).rejects.toThrow(NotFoundError);
  });
});

describe('crearTorneo / actualizarTorneo / eliminarTorneo', () => {
  it('crea si la temporada existe', async () => {
    await expect(crearTorneo({ temporadaId: 9, nombre: 'APERTURA' })).resolves.toMatchObject({ temporadaId: 9 });
  });

  it('rechaza si la temporada no existe', async () => {
    mockedPrisma.temporada.findUnique.mockResolvedValue(null);
    await expect(crearTorneo({ temporadaId: 999, nombre: 'APERTURA' })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.torneo.create).not.toHaveBeenCalled();
  });

  it('actualiza y elimina tras verificar que existe', async () => {
    await actualizarTorneo(1, { nombre: 'CLAUSURA' });
    expect(mockedPrisma.torneo.update).toHaveBeenCalledWith({ where: { id: 1 }, data: { nombre: 'CLAUSURA' } });
    await eliminarTorneo(1);
    expect(mockedPrisma.torneo.delete).toHaveBeenCalledWith({ where: { id: 1 } });
  });

  it('no actualiza ni elimina si no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(actualizarTorneo(999, { nombre: 'CLAUSURA' })).rejects.toThrow(NotFoundError);
    await expect(eliminarTorneo(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.torneo.update).not.toHaveBeenCalled();
    expect(mockedPrisma.torneo.delete).not.toHaveBeenCalled();
  });
});
