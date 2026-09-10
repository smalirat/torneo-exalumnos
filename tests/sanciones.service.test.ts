import { prisma } from '../src/lib/prisma';
import { actualizarSancion, crearSancion, listarAmonestados, listarSancionados } from '../src/modules/sanciones/sanciones.service';
import { NotFoundError, ValidationError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    jugador: { findUnique: jest.fn() },
    equipo: { findUnique: jest.fn() },
    torneo: { findUnique: jest.fn() },
    sancion: { findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), findMany: jest.fn() },
    amonestacion: { findMany: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  jugador: { findUnique: jest.Mock };
  equipo: { findUnique: jest.Mock };
  torneo: { findUnique: jest.Mock };
  sancion: { findUnique: jest.Mock; create: jest.Mock; update: jest.Mock; findMany: jest.Mock };
  amonestacion: { findMany: jest.Mock };
};

const input = {
  jugadorId: 1,
  equipoId: 2,
  torneoId: 3,
  tipoTarjeta: 'ROJA' as const,
  fechasSuspension: 1,
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.jugador.findUnique.mockResolvedValue({ id: 1, nombre: 'Juan' });
  mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 2, nombre: 'GULP' });
  mockedPrisma.torneo.findUnique.mockResolvedValue({ id: 3, nombre: 'APERTURA' });
  mockedPrisma.sancion.create.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));
});

describe('crearSancion', () => {
  it('crea la sanción cuando jugador/equipo/torneo existen', async () => {
    const resultado = await crearSancion(input);
    expect(resultado).toMatchObject(input);
  });

  it('rechaza si el jugador no existe', async () => {
    mockedPrisma.jugador.findUnique.mockResolvedValue(null);
    await expect(crearSancion(input)).rejects.toThrow(NotFoundError);
  });

  it('rechaza si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(crearSancion(input)).rejects.toThrow(NotFoundError);
  });
});

describe('actualizarSancion', () => {
  const sancionExistente = {
    id: 1,
    jugadorId: 1,
    equipoId: 2,
    torneoId: 3,
    tipoTarjeta: 'ROJA',
    fechasSuspension: 1,
    fechasCumplidas: 0,
    estado: 'PENDIENTE',
    observaciones: null,
  };

  beforeEach(() => {
    mockedPrisma.sancion.findUnique.mockResolvedValue(sancionExistente);
    mockedPrisma.sancion.update.mockImplementation(({ data }) =>
      Promise.resolve({ ...sancionExistente, ...data }),
    );
  });

  it('marca la sanción como cumplida', async () => {
    const resultado = await actualizarSancion(1, { estado: 'CUMPLIDA' });
    expect(resultado).toMatchObject({ estado: 'CUMPLIDA' });
  });

  it('rechaza reabrir (PENDIENTE) una sanción ya CUMPLIDA', async () => {
    mockedPrisma.sancion.findUnique.mockResolvedValue({ ...sancionExistente, estado: 'CUMPLIDA' });
    await expect(actualizarSancion(1, { estado: 'PENDIENTE' })).rejects.toThrow(ValidationError);
  });

  it('lanza NotFoundError si la sanción no existe', async () => {
    mockedPrisma.sancion.findUnique.mockResolvedValue(null);
    await expect(actualizarSancion(999, { estado: 'CUMPLIDA' })).rejects.toThrow(NotFoundError);
  });
});

describe('listarSancionados', () => {
  const torneoMock = { id: 3, temporadaId: 7, nombre: 'APERTURA' };

  beforeEach(() => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(torneoMock);
    mockedPrisma.sancion.findMany.mockResolvedValue([]);
  });

  it('sin filtro muestra lo vigente (PENDIENTE + EN_TRIBUNAL) del torneo por temporada', async () => {
    await listarSancionados(3);
    expect(mockedPrisma.sancion.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({
          estado: { in: ['PENDIENTE', 'EN_TRIBUNAL'] },
          torneo: { temporadaId: 7 },
        }),
      }),
    );
  });

  it('pendiente=true mantiene el filtro de vigentes', async () => {
    await listarSancionados(3, true);
    expect(mockedPrisma.sancion.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ estado: { in: ['PENDIENTE', 'EN_TRIBUNAL'] } }),
      }),
    );
  });

  it('pendiente=false muestra solo CUMPLIDA', async () => {
    await listarSancionados(3, false);
    expect(mockedPrisma.sancion.findMany).toHaveBeenCalledWith(
      expect.objectContaining({
        where: expect.objectContaining({ estado: { in: ['CUMPLIDA'] } }),
      }),
    );
  });

  it('lanza NotFoundError si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(listarSancionados(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.sancion.findMany).not.toHaveBeenCalled();
  });
});

describe('listarAmonestados', () => {
  it('lista las amonestaciones del torneo', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue({ id: 3 });
    mockedPrisma.amonestacion.findMany.mockResolvedValue([{ id: 1, cantidad: 4 }]);
    const resultado = await listarAmonestados(3);
    expect(resultado).toHaveLength(1);
    expect(mockedPrisma.amonestacion.findMany).toHaveBeenCalledWith(
      expect.objectContaining({ where: { torneoId: 3 } }),
    );
  });

  it('lanza NotFoundError si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(listarAmonestados(999)).rejects.toThrow(NotFoundError);
  });
});
