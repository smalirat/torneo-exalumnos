import { prisma } from '../../src/lib/prisma';
import { actualizarSancion, crearSancion } from '../../src/modules/sanciones/sanciones.service';
import { NotFoundError, ValidationError } from '../../src/utils/AppError';

jest.mock('../../src/lib/prisma', () => ({
  prisma: {
    jugador: { findUnique: jest.fn() },
    equipo: { findUnique: jest.fn() },
    torneo: { findUnique: jest.fn() },
    sancion: { findUnique: jest.fn(), create: jest.fn(), update: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  jugador: { findUnique: jest.Mock };
  equipo: { findUnique: jest.Mock };
  torneo: { findUnique: jest.Mock };
  sancion: { findUnique: jest.Mock; create: jest.Mock; update: jest.Mock };
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
    cumplida: false,
    pendiente: true,
    observaciones: null,
  };

  beforeEach(() => {
    mockedPrisma.sancion.findUnique.mockResolvedValue(sancionExistente);
    mockedPrisma.sancion.update.mockImplementation(({ data }) =>
      Promise.resolve({ ...sancionExistente, ...data }),
    );
  });

  it('marcar cumplida=true apaga pendiente automáticamente si no se especifica', async () => {
    const resultado = await actualizarSancion(1, { cumplida: true });
    expect(resultado).toMatchObject({ cumplida: true, pendiente: false });
  });

  it('rechaza reabrir (pendiente=true) una sanción ya cumplida sin aclarar cumplida=false', async () => {
    mockedPrisma.sancion.findUnique.mockResolvedValue({ ...sancionExistente, cumplida: true, pendiente: false });
    await expect(actualizarSancion(1, { pendiente: true })).rejects.toThrow(ValidationError);
  });

  it('lanza NotFoundError si la sanción no existe', async () => {
    mockedPrisma.sancion.findUnique.mockResolvedValue(null);
    await expect(actualizarSancion(999, { cumplida: true })).rejects.toThrow(NotFoundError);
  });
});
