import { prisma } from '../src/lib/prisma';
import {
  actualizarEquipo,
  crearEquipo,
  eliminarEquipo,
  listarEquipos,
  obtenerEquipo,
  obtenerPlantel,
  obtenerStatsEquipoActual,
} from '../src/modules/equipos/equipos.service';
import { obtenerStatsEquipo } from '../src/modules/standings/standings.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    equipo: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
    jugador: { findMany: jest.fn() },
    inscripcionEquipo: { findFirst: jest.fn() },
  },
}));
jest.mock('../src/modules/standings/standings.service', () => ({
  obtenerStatsEquipo: jest.fn(),
}));

const mockedPrisma = prisma as unknown as {
  equipo: { findMany: jest.Mock; findUnique: jest.Mock; create: jest.Mock; update: jest.Mock; delete: jest.Mock };
  jugador: { findMany: jest.Mock };
  inscripcionEquipo: { findFirst: jest.Mock };
};
const mockedStats = obtenerStatsEquipo as unknown as jest.Mock;

const equipoMock = { id: 1, nombre: 'GULP' };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.equipo.findUnique.mockResolvedValue(equipoMock);
});

describe('listarEquipos / obtenerEquipo', () => {
  it('lista ordenado por nombre', async () => {
    mockedPrisma.equipo.findMany.mockResolvedValue([equipoMock]);
    await listarEquipos();
    expect(mockedPrisma.equipo.findMany).toHaveBeenCalledWith({ orderBy: { nombre: 'asc' } });
  });

  it('obtiene por id', async () => {
    await expect(obtenerEquipo(1)).resolves.toEqual(equipoMock);
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(obtenerEquipo(999)).rejects.toThrow(NotFoundError);
  });
});

describe('crearEquipo / actualizarEquipo / eliminarEquipo', () => {
  it('crea con el nombre', async () => {
    mockedPrisma.equipo.create.mockResolvedValue({ id: 2, nombre: 'BOCHA FC' });
    await expect(crearEquipo({ nombre: 'BOCHA FC' })).resolves.toMatchObject({ nombre: 'BOCHA FC' });
  });

  it('actualiza tras verificar que existe', async () => {
    mockedPrisma.equipo.update.mockResolvedValue({ ...equipoMock, nombre: 'GULP FC' });
    await actualizarEquipo(1, { nombre: 'GULP FC' });
    expect(mockedPrisma.equipo.update).toHaveBeenCalledWith({ where: { id: 1 }, data: { nombre: 'GULP FC' } });
  });

  it('no actualiza si no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(actualizarEquipo(999, { nombre: 'X' })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.equipo.update).not.toHaveBeenCalled();
  });

  it('elimina tras verificar que existe', async () => {
    await eliminarEquipo(1);
    expect(mockedPrisma.equipo.delete).toHaveBeenCalledWith({ where: { id: 1 } });
  });

  it('no elimina si no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(eliminarEquipo(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.equipo.delete).not.toHaveBeenCalled();
  });
});

describe('obtenerPlantel', () => {
  it('devuelve los jugadores ordenados', async () => {
    mockedPrisma.jugador.findMany.mockResolvedValue([{ id: 1, nombre: 'Juan' }]);
    await obtenerPlantel(1);
    expect(mockedPrisma.jugador.findMany).toHaveBeenCalledWith({
      where: { equipoId: 1 },
      orderBy: { nombre: 'asc' },
    });
  });

  it('lanza NotFoundError si el equipo no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(obtenerPlantel(999)).rejects.toThrow(NotFoundError);
  });
});

describe('obtenerStatsEquipoActual', () => {
  it('con categoriaId delega directo en standings', async () => {
    mockedStats.mockResolvedValue({ posicion: 1 });
    await obtenerStatsEquipoActual(1, 10, 20);
    expect(mockedStats).toHaveBeenCalledWith(1, 10, 20);
  });

  it('sin categoriaId usa la inscripción más reciente', async () => {
    mockedStats.mockResolvedValue({ posicion: 2 });
    mockedPrisma.inscripcionEquipo.findFirst.mockResolvedValue({ categoriaId: 10, zonaId: 20 });
    await obtenerStatsEquipoActual(1);
    expect(mockedStats).toHaveBeenCalledWith(1, 10, 20);
  });

  it('sin inscripciones lanza NotFoundError', async () => {
    mockedPrisma.inscripcionEquipo.findFirst.mockResolvedValue(null);
    await expect(obtenerStatsEquipoActual(1)).rejects.toThrow(NotFoundError);
    expect(mockedStats).not.toHaveBeenCalled();
  });
});
