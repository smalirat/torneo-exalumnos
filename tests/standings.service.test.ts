import { prisma } from '../src/lib/prisma';
import { obtenerStandings, obtenerStatsEquipo } from '../src/modules/standings/standings.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    categoria: { findUnique: jest.fn() },
    zona: { findUnique: jest.fn() },
    inscripcionEquipo: { findMany: jest.fn() },
    partido: { findMany: jest.fn() },
    puntosPresentismo: { findMany: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  categoria: { findUnique: jest.Mock };
  zona: { findUnique: jest.Mock };
  inscripcionEquipo: { findMany: jest.Mock };
  partido: { findMany: jest.Mock };
  puntosPresentismo: { findMany: jest.Mock };
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 10, torneoId: 1, nombre: 'A' });
  mockedPrisma.inscripcionEquipo.findMany.mockResolvedValue([
    { equipoId: 1, equipo: { nombre: 'GULP' } },
    { equipoId: 2, equipo: { nombre: 'BOCHA FC' } },
  ]);
  mockedPrisma.partido.findMany.mockResolvedValue([
    { equipoLocalId: 1, equipoVisitanteId: 2, golesLocal: 2, golesVisitante: 0 },
  ]);
  mockedPrisma.puntosPresentismo.findMany.mockResolvedValue([{ equipoId: 1, puntos: 5 }]);
});

describe('obtenerStandings', () => {
  it('mapea inscripciones/partidos/PR y calcula la tabla (PR suma)', async () => {
    const tabla = await obtenerStandings(10);
    // GULP: 3 ptos de cancha + 5 PR = 8; BOCHA queda abajo con 0
    expect(tabla.find((f) => f.equipoId === 1)).toMatchObject({ ptos: 8, pr: 5 });
    expect(tabla.findIndex((f) => f.equipoId === 1)).toBeLessThan(
      tabla.findIndex((f) => f.equipoId === 2),
    );
  });

  it('filtra partidos JUGADO con goles y excluye playoff (jornada >= 900)', async () => {
    await obtenerStandings(10);
    expect(mockedPrisma.partido.findMany).toHaveBeenCalledWith({
      where: expect.objectContaining({
        categoriaId: 10,
        jornada: { lt: 900 },
        estado: 'JUGADO',
        golesLocal: { not: null },
        golesVisitante: { not: null },
      }),
    });
  });

  it('propaga zonaId a los tres queries', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue({ id: 20, categoriaId: 10 });
    await obtenerStandings(10, 20);
    expect(mockedPrisma.inscripcionEquipo.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: expect.objectContaining({ zonaId: 20 }) }),
    );
    expect(mockedPrisma.puntosPresentismo.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: expect.objectContaining({ zonaId: 20 }) }),
    );
  });

  it('rechaza si la categoría no existe (sin consultar el resto)', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(obtenerStandings(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.inscripcionEquipo.findMany).not.toHaveBeenCalled();
  });

  it('rechaza si la zona no pertenece a la categoría', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue({ id: 20, categoriaId: 777 });
    await expect(obtenerStandings(10, 20)).rejects.toThrow();
    expect(mockedPrisma.inscripcionEquipo.findMany).not.toHaveBeenCalled();
  });
});

describe('obtenerStatsEquipo', () => {
  it('devuelve la fila del equipo', async () => {
    await expect(obtenerStatsEquipo(2, 10)).resolves.toMatchObject({ equipoId: 2, ptos: 0 });
  });

  it('lanza NotFoundError si el equipo no está inscripto ahí', async () => {
    await expect(obtenerStatsEquipo(999, 10)).rejects.toThrow(NotFoundError);
  });
});
