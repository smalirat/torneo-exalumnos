import { prisma } from '../src/lib/prisma';
import { listarFiguras } from '../src/modules/figuras/figuras.service';
import { listarGoleadores } from '../src/modules/goleadores/goleadores.service';
import { listarImbatibles } from '../src/modules/imbatibles/imbatibles.service';
import { NotFoundError } from '../src/utils/AppError';

// Los tres rankings comparten forma: validar torneo + findMany ordenado.
// (Los upserts establecer* ya están cubiertos vía excelImport.service.test.ts.)
jest.mock('../src/lib/prisma', () => ({
  prisma: {
    torneo: { findUnique: jest.fn() },
    goleador: { findMany: jest.fn() },
    figura: { findMany: jest.fn() },
    imbatible: { findMany: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  torneo: { findUnique: jest.Mock };
  goleador: { findMany: jest.Mock };
  figura: { findMany: jest.Mock };
  imbatible: { findMany: jest.Mock };
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.torneo.findUnique.mockResolvedValue({ id: 3, nombre: 'APERTURA' });
});

describe('listarGoleadores', () => {
  it('ordena por goles descendente con jugador+equipo', async () => {
    mockedPrisma.goleador.findMany.mockResolvedValue([{ goles: 8 }]);
    await expect(listarGoleadores(3)).resolves.toHaveLength(1);
    expect(mockedPrisma.goleador.findMany).toHaveBeenCalledWith({
      where: { torneoId: 3 },
      include: { jugador: { include: { equipo: true } } },
      orderBy: { goles: 'desc' },
    });
  });

  it('lanza NotFoundError si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(listarGoleadores(999)).rejects.toThrow(NotFoundError);
  });
});

describe('listarFiguras', () => {
  it('ordena por veces descendente', async () => {
    mockedPrisma.figura.findMany.mockResolvedValue([]);
    await listarFiguras(3);
    expect(mockedPrisma.figura.findMany).toHaveBeenCalledWith({
      where: { torneoId: 3 },
      include: { jugador: { include: { equipo: true } } },
      orderBy: { veces: 'desc' },
    });
  });

  it('lanza NotFoundError si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(listarFiguras(999)).rejects.toThrow(NotFoundError);
  });
});

describe('listarImbatibles', () => {
  it('ordena por golesRecibidos ASCENDENTE (menos goleado primero)', async () => {
    mockedPrisma.imbatible.findMany.mockResolvedValue([]);
    await listarImbatibles(3);
    expect(mockedPrisma.imbatible.findMany).toHaveBeenCalledWith({
      where: { torneoId: 3 },
      include: { jugador: { include: { equipo: true } } },
      orderBy: { golesRecibidos: 'asc' },
    });
  });

  it('lanza NotFoundError si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(listarImbatibles(999)).rejects.toThrow(NotFoundError);
  });
});
