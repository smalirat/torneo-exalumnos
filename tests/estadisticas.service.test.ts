import { prisma } from '../src/lib/prisma';
import { actualizarEstadisticas } from '../src/modules/estadisticas/estadisticas.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    jugador: { findUnique: jest.fn() },
    torneo: { findUnique: jest.fn() },
    goleador: { upsert: jest.fn() },
    figura: { upsert: jest.fn() },
    imbatible: { upsert: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  jugador: { findUnique: jest.Mock };
  torneo: { findUnique: jest.Mock };
  goleador: { upsert: jest.Mock };
  figura: { upsert: jest.Mock };
  imbatible: { upsert: jest.Mock };
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.jugador.findUnique.mockResolvedValue({ id: 1, nombre: 'Juan' });
  mockedPrisma.torneo.findUnique.mockResolvedValue({ id: 3, nombre: 'APERTURA' });
  mockedPrisma.goleador.upsert.mockImplementation(({ create }) => Promise.resolve({ ...create }));
  mockedPrisma.figura.upsert.mockImplementation(({ create }) => Promise.resolve({ ...create }));
  mockedPrisma.imbatible.upsert.mockImplementation(({ create }) => Promise.resolve({ ...create }));
});

describe('actualizarEstadisticas', () => {
  it('actualiza solo goles cuando es lo único que viene', async () => {
    const resultado = await actualizarEstadisticas({ jugadorId: 1, torneoId: 3, goles: 2 });
    expect(mockedPrisma.goleador.upsert).toHaveBeenCalledWith({
      where: { jugadorId_torneoId: { jugadorId: 1, torneoId: 3 } },
      create: { jugadorId: 1, torneoId: 3, goles: 2 },
      update: { goles: { increment: 2 } },
    });
    expect(mockedPrisma.figura.upsert).not.toHaveBeenCalled();
    expect(mockedPrisma.imbatible.upsert).not.toHaveBeenCalled();
    expect(resultado.goleador).toMatchObject({ goles: 2 });
  });

  it('actualiza las tres estadísticas juntas', async () => {
    const resultado = await actualizarEstadisticas({ jugadorId: 1, torneoId: 3, goles: 1, mvp: 2, golesRecibidos: 0 });
    expect(mockedPrisma.goleador.upsert).toHaveBeenCalled();
    expect(mockedPrisma.figura.upsert).toHaveBeenCalledWith(
      expect.objectContaining({
        create: expect.objectContaining({ veces: 2 }),
        update: { veces: { increment: 2 } },
      }),
    );
    expect(mockedPrisma.imbatible.upsert).toHaveBeenCalled();
    expect(Object.keys(resultado)).toHaveLength(3);
  });

  it('sin deltas no toca nada y devuelve vacío', async () => {
    await expect(actualizarEstadisticas({ jugadorId: 1, torneoId: 3 })).resolves.toEqual({});
    expect(mockedPrisma.goleador.upsert).not.toHaveBeenCalled();
    expect(mockedPrisma.figura.upsert).not.toHaveBeenCalled();
    expect(mockedPrisma.imbatible.upsert).not.toHaveBeenCalled();
  });

  it('rechaza si el jugador no existe', async () => {
    mockedPrisma.jugador.findUnique.mockResolvedValue(null);
    await expect(actualizarEstadisticas({ jugadorId: 999, torneoId: 3, goles: 1 })).rejects.toThrow(NotFoundError);
  });

  it('rechaza si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(actualizarEstadisticas({ jugadorId: 1, torneoId: 999, goles: 1 })).rejects.toThrow(NotFoundError);
  });
});
