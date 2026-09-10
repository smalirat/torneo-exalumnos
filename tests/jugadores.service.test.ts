import { prisma } from '../src/lib/prisma';
import {
  actualizarJugador,
  crearJugador,
  eliminarJugador,
  listarJugadores,
  obtenerJugador,
} from '../src/modules/jugadores/jugadores.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    equipo: { findUnique: jest.fn() },
    jugador: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  equipo: { findUnique: jest.Mock };
  jugador: { findMany: jest.Mock; findUnique: jest.Mock; create: jest.Mock; update: jest.Mock; delete: jest.Mock };
};

const jugadorMock = { id: 1, nombre: 'Juan', equipoId: 2 };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.jugador.findUnique.mockResolvedValue(jugadorMock);
  mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 2, nombre: 'GULP' });
  mockedPrisma.jugador.create.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));
});

describe('listarJugadores / obtenerJugador', () => {
  it('lista con filtro opcional incluyendo equipo', async () => {
    await listarJugadores(2);
    expect(mockedPrisma.jugador.findMany).toHaveBeenCalledWith({
      where: { equipoId: 2 },
      include: { equipo: true },
      orderBy: { nombre: 'asc' },
    });
  });

  it('obtiene por id', async () => {
    await expect(obtenerJugador(1)).resolves.toEqual(jugadorMock);
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.jugador.findUnique.mockResolvedValue(null);
    await expect(obtenerJugador(999)).rejects.toThrow(NotFoundError);
  });
});

describe('crearJugador', () => {
  it('crea sin equipo', async () => {
    await expect(crearJugador({ nombre: 'Juan' })).resolves.toMatchObject({ nombre: 'Juan' });
    expect(mockedPrisma.equipo.findUnique).not.toHaveBeenCalled();
  });

  it('crea con equipo válido', async () => {
    await crearJugador({ nombre: 'Juan', equipoId: 2 });
    expect(mockedPrisma.equipo.findUnique).toHaveBeenCalledWith({ where: { id: 2 } });
    expect(mockedPrisma.jugador.create).toHaveBeenCalled();
  });

  it('rechaza si el equipo no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(crearJugador({ nombre: 'Juan', equipoId: 999 })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.jugador.create).not.toHaveBeenCalled();
  });
});

describe('actualizarJugador / eliminarJugador', () => {
  it('actualiza y valida el equipo nuevo si viene', async () => {
    await actualizarJugador(1, { equipoId: 2 });
    expect(mockedPrisma.equipo.findUnique).toHaveBeenCalledWith({ where: { id: 2 } });
    expect(mockedPrisma.jugador.update).toHaveBeenCalledWith({ where: { id: 1 }, data: { equipoId: 2 } });
  });

  it('actualiza sin validar equipo si no viene', async () => {
    await actualizarJugador(1, { nombre: 'Juancho' });
    expect(mockedPrisma.equipo.findUnique).not.toHaveBeenCalled();
  });

  it('rechaza equipo inexistente al actualizar', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(actualizarJugador(1, { equipoId: 999 })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.jugador.update).not.toHaveBeenCalled();
  });

  it('elimina tras verificar que existe', async () => {
    await eliminarJugador(1);
    expect(mockedPrisma.jugador.delete).toHaveBeenCalledWith({ where: { id: 1 } });
  });

  it('no actualiza ni elimina si no existe', async () => {
    mockedPrisma.jugador.findUnique.mockResolvedValue(null);
    await expect(actualizarJugador(999, { nombre: 'X' })).rejects.toThrow(NotFoundError);
    await expect(eliminarJugador(999)).rejects.toThrow(NotFoundError);
  });
});
