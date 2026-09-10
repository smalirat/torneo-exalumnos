import { prisma } from '../src/lib/prisma';
import { eliminarAmonestacion, registrarAmonestacion } from '../src/modules/amonestaciones/amonestaciones.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    jugador: { findUnique: jest.fn() },
    equipo: { findUnique: jest.fn() },
    torneo: { findUnique: jest.fn() },
    amonestacion: { upsert: jest.fn(), findUnique: jest.fn(), update: jest.fn(), delete: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  jugador: { findUnique: jest.Mock };
  equipo: { findUnique: jest.Mock };
  torneo: { findUnique: jest.Mock };
  amonestacion: { upsert: jest.Mock; findUnique: jest.Mock; update: jest.Mock; delete: jest.Mock };
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.jugador.findUnique.mockResolvedValue({ id: 1, nombre: 'Juan' });
  mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 2, nombre: 'GULP' });
  mockedPrisma.torneo.findUnique.mockResolvedValue({ id: 3, nombre: 'APERTURA' });
  mockedPrisma.amonestacion.upsert.mockImplementation(({ create }) => Promise.resolve({ id: 1, ...create }));
});

describe('registrarAmonestacion', () => {
  it('carga +1 por defecto con upsert (acumula vía increment)', async () => {
    await registrarAmonestacion({ jugadorId: 1, equipoId: 2, torneoId: 3 });
    expect(mockedPrisma.amonestacion.upsert).toHaveBeenCalledWith({
      where: { jugadorId_torneoId: { jugadorId: 1, torneoId: 3 } },
      create: { jugadorId: 1, equipoId: 2, torneoId: 3, cantidad: 1 },
      update: { cantidad: { increment: 1 } },
    });
  });

  it('permite cargar varias amarillas de una', async () => {
    await registrarAmonestacion({ jugadorId: 1, equipoId: 2, torneoId: 3, cantidad: 3 });
    expect(mockedPrisma.amonestacion.upsert).toHaveBeenCalledWith(
      expect.objectContaining({
        create: expect.objectContaining({ cantidad: 3 }),
        update: { cantidad: { increment: 3 } },
      }),
    );
  });

  it.each([
    ['jugador', { jugador: null, equipo: { id: 2 }, torneo: { id: 3 } }],
    ['equipo', { jugador: { id: 1 }, equipo: null, torneo: { id: 3 } }],
    ['torneo', { jugador: { id: 1 }, equipo: { id: 2 }, torneo: null }],
  ])('rechaza si el %s no existe', async (_caso, refs) => {
    mockedPrisma.jugador.findUnique.mockResolvedValue(refs.jugador);
    mockedPrisma.equipo.findUnique.mockResolvedValue(refs.equipo);
    mockedPrisma.torneo.findUnique.mockResolvedValue(refs.torneo);
    await expect(registrarAmonestacion({ jugadorId: 1, equipoId: 2, torneoId: 3 })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.amonestacion.upsert).not.toHaveBeenCalled();
  });
});

describe('eliminarAmonestacion', () => {
  it('decrementa si quedan amarillas (3 -> 2, sin borrar)', async () => {
    mockedPrisma.amonestacion.findUnique.mockResolvedValue({ id: 7, cantidad: 3 });
    await eliminarAmonestacion(7);
    expect(mockedPrisma.amonestacion.update).toHaveBeenCalledWith({ where: { id: 7 }, data: { cantidad: 2 } });
    expect(mockedPrisma.amonestacion.delete).not.toHaveBeenCalled();
  });

  it('borra el registro cuando era la última amarilla', async () => {
    mockedPrisma.amonestacion.findUnique.mockResolvedValue({ id: 7, cantidad: 1 });
    await eliminarAmonestacion(7);
    expect(mockedPrisma.amonestacion.delete).toHaveBeenCalledWith({ where: { id: 7 } });
    expect(mockedPrisma.amonestacion.update).not.toHaveBeenCalled();
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.amonestacion.findUnique.mockResolvedValue(null);
    await expect(eliminarAmonestacion(999)).rejects.toThrow(NotFoundError);
  });
});
