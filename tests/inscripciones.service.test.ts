import { prisma } from '../src/lib/prisma';
import { crearInscripcion } from '../src/modules/inscripciones/inscripciones.service';
import { ConflictError, NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => {
  const equipo = { findUnique: jest.fn() };
  const categoria = { findUnique: jest.fn() };
  const zona = { findUnique: jest.fn() };
  const inscripcionEquipo = { findFirst: jest.fn(), create: jest.fn() };
  // crearInscripcion envuelve todo en prisma.$transaction: en tests
  // ejecutamos el callback directamente con los mismos delegados mockeados.
  const tx = { equipo, categoria, zona, inscripcionEquipo };
  return {
    prisma: {
      ...tx,
      $transaction: jest.fn((cb: (t: typeof tx) => unknown) => cb(tx)),
    },
  };
});

const mockedPrisma = prisma as unknown as {
  equipo: { findUnique: jest.Mock };
  categoria: { findUnique: jest.Mock };
  zona: { findUnique: jest.Mock };
  inscripcionEquipo: { findFirst: jest.Mock; create: jest.Mock };
  $transaction: jest.Mock;
};

const input = { equipoId: 1, categoriaId: 10 };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 1, nombre: 'GULP' });
  mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 10, torneoId: 100, nombre: 'A', zonas: [] });
  mockedPrisma.inscripcionEquipo.findFirst.mockResolvedValue(null);
  mockedPrisma.inscripcionEquipo.create.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));
});

describe('crearInscripcion', () => {
  it('crea la inscripción cuando el equipo no tiene otra en ese torneo', async () => {
    const resultado = await crearInscripcion(input);
    expect(resultado).toMatchObject(input);
    expect(mockedPrisma.inscripcionEquipo.create).toHaveBeenCalled();
  });

  it('rechaza si el equipo ya está inscripto en otra categoría del mismo torneo', async () => {
    mockedPrisma.inscripcionEquipo.findFirst.mockResolvedValue({
      id: 5,
      categoria: { nombre: 'B' },
      zona: null,
    });

    await expect(crearInscripcion(input)).rejects.toThrow(ConflictError);
    expect(mockedPrisma.inscripcionEquipo.create).not.toHaveBeenCalled();
  });

  it('rechaza si el equipo no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(crearInscripcion(input)).rejects.toThrow(NotFoundError);
  });

  it('rechaza si la categoría no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(crearInscripcion(input)).rejects.toThrow(NotFoundError);
  });

  it('rechaza si la zona no pertenece a la categoría', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue({ id: 50, categoriaId: 999 });
    await expect(crearInscripcion({ ...input, zonaId: 50 })).rejects.toThrow();
  });
});
