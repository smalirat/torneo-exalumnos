import { EstadoPartido } from '@prisma/client';
import { prisma } from '../../src/lib/prisma';
import { crearPartido, actualizarPartido } from '../../src/modules/partidos/partidos.service';
import { ConflictError, NotFoundError, ValidationError } from '../../src/utils/AppError';

// Mockeamos Prisma completo: estos son tests de LÓGICA DE NEGOCIO
// (duplicados, inferencia de estado, validaciones), no de integración
// con Postgres. Los tests de integración con DB real van aparte (ver
// tests/partidos/partidos.routes.integration.test.ts, a agregar cuando
// tengas una DB de test levantada).
jest.mock('../../src/lib/prisma', () => ({
  prisma: {
    categoria: { findUnique: jest.fn() },
    zona: { findUnique: jest.fn() },
    equipo: { findMany: jest.fn() },
    partido: {
      findFirst: jest.fn(),
      findUnique: jest.fn(),
      create: jest.fn(),
      update: jest.fn(),
    },
  },
}));

const mockedPrisma = prisma as unknown as {
  categoria: { findUnique: jest.Mock };
  zona: { findUnique: jest.Mock };
  equipo: { findMany: jest.Mock };
  partido: {
    findFirst: jest.Mock;
    findUnique: jest.Mock;
    create: jest.Mock;
    update: jest.Mock;
  };
};

const inputBase = {
  categoriaId: 1,
  equipoLocalId: 10,
  equipoVisitanteId: 20,
  fecha: new Date('2026-04-12'),
  jornada: 3,
};

beforeEach(() => {
  jest.clearAllMocks();
  // Defaults "felices" para no repetir esto en cada test
  mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 1, torneoId: 1, nombre: 'A' });
  mockedPrisma.equipo.findMany.mockResolvedValue([
    { id: 10, nombre: 'GULP' },
    { id: 20, nombre: 'BOCHA FC' },
  ]);
  mockedPrisma.partido.findFirst.mockResolvedValue(null); // no hay duplicado
});

describe('crearPartido', () => {
  it('crea un partido PENDIENTE cuando no se cargan goles', async () => {
    mockedPrisma.partido.create.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));

    await crearPartido(inputBase as never);

    expect(mockedPrisma.partido.create).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ estado: EstadoPartido.PENDIENTE }) }),
    );
  });

  it('crea un partido JUGADO cuando se cargan ambos goles', async () => {
    mockedPrisma.partido.create.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));

    await crearPartido({ ...inputBase, golesLocal: 2, golesVisitante: 1 } as never);

    expect(mockedPrisma.partido.create).toHaveBeenCalledWith(
      expect.objectContaining({ data: expect.objectContaining({ estado: EstadoPartido.JUGADO }) }),
    );
  });

  it('rechaza un partido duplicado (misma fecha, mismos equipos)', async () => {
    mockedPrisma.partido.findFirst.mockResolvedValue({ id: 99 }); // ya existe

    await expect(crearPartido(inputBase as never)).rejects.toThrow(ConflictError);
    expect(mockedPrisma.partido.create).not.toHaveBeenCalled();
  });

  it('rechaza si la categoría no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);

    await expect(crearPartido(inputBase as never)).rejects.toThrow(NotFoundError);
  });

  it('rechaza si algún equipo no existe', async () => {
    mockedPrisma.equipo.findMany.mockResolvedValue([{ id: 10, nombre: 'GULP' }]); // falta el 20

    await expect(crearPartido(inputBase as never)).rejects.toThrow(NotFoundError);
  });

  it('rechaza si la zona no pertenece a la categoría', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue({ id: 5, categoriaId: 999 }); // otra categoría

    await expect(crearPartido({ ...inputBase, zonaId: 5 } as never)).rejects.toThrow(ValidationError);
  });
});

describe('actualizarPartido', () => {
  const partidoExistente = {
    id: 1,
    categoriaId: 1,
    zonaId: null,
    equipoLocalId: 10,
    equipoVisitanteId: 20,
    golesLocal: 3,
    golesVisitante: 0,
    fecha: new Date('2026-04-12'),
    jornada: 3,
    campo: 'Cancha 1',
    cancha: '1',
    horario: '10:00',
    estado: EstadoPartido.JUGADO,
  };

  beforeEach(() => {
    mockedPrisma.partido.findUnique.mockResolvedValue(partidoExistente);
    mockedPrisma.partido.update.mockImplementation(({ data }) =>
      Promise.resolve({ ...partidoExistente, ...data }),
    );
  });

  it('corrige el resultado y el estado se recalcula (no arrastra el estado viejo)', async () => {
    await actualizarPartido(1, { golesLocal: 1, golesVisitante: 1 });

    expect(mockedPrisma.partido.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ golesLocal: 1, golesVisitante: 1, estado: EstadoPartido.JUGADO }),
      }),
    );
  });

  it('permite volver un partido a PENDIENTE poniendo los goles en null', async () => {
    await actualizarPartido(1, { golesLocal: null, golesVisitante: null });

    expect(mockedPrisma.partido.update).toHaveBeenCalledWith(
      expect.objectContaining({
        data: expect.objectContaining({ golesLocal: null, golesVisitante: null, estado: EstadoPartido.PENDIENTE }),
      }),
    );
  });

  it('lanza NotFoundError si el partido no existe', async () => {
    mockedPrisma.partido.findUnique.mockResolvedValue(null);
    await expect(actualizarPartido(999, { golesLocal: 1, golesVisitante: 1 })).rejects.toThrow(NotFoundError);
  });

  it('rechaza si al cambiar equipos quedan iguales', async () => {
    await expect(actualizarPartido(1, { equipoLocalId: 20, equipoVisitanteId: 20 })).rejects.toThrow(
      ValidationError,
    );
  });

  it('vuelve a chequear duplicados si se cambia la fecha o los equipos', async () => {
    mockedPrisma.partido.findFirst.mockResolvedValue({ id: 555 }); // choca con otro partido

    await expect(actualizarPartido(1, { fecha: new Date('2026-05-01') })).rejects.toThrow(ConflictError);
  });

  it('NO vuelve a chequear duplicados si solo se corrige el resultado (mismo partido)', async () => {
    await actualizarPartido(1, { golesLocal: 5, golesVisitante: 5 });
    // findFirst no debería llamarse porque fecha/equipos no cambiaron
    expect(mockedPrisma.partido.findFirst).not.toHaveBeenCalled();
  });
});
