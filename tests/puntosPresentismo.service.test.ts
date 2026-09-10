import { prisma } from '../src/lib/prisma';
import { crearPuntosPresentismo, eliminarPuntosPresentismo } from '../src/modules/puntosPresentismo/puntosPresentismo.service';
import { NotFoundError, ValidationError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    equipo: { findUnique: jest.fn() },
    categoria: { findUnique: jest.fn() },
    zona: { findUnique: jest.fn() },
    puntosPresentismo: { create: jest.fn(), findUnique: jest.fn(), delete: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  equipo: { findUnique: jest.Mock };
  categoria: { findUnique: jest.Mock };
  zona: { findUnique: jest.Mock };
  puntosPresentismo: { create: jest.Mock; findUnique: jest.Mock; delete: jest.Mock };
};

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 1, nombre: 'GULP' });
  mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 10, torneoId: 100, nombre: 'B' });
  mockedPrisma.puntosPresentismo.create.mockImplementation(({ data }) => Promise.resolve({ id: 1, ...data }));
});

describe('crearPuntosPresentismo', () => {
  it('crea presentismo sin zona para categoría directa', async () => {
    const resultado = await crearPuntosPresentismo({ equipoId: 1, categoriaId: 10, puntos: 3 });
    expect(mockedPrisma.puntosPresentismo.create).toHaveBeenCalledWith({
      data: { equipoId: 1, categoriaId: 10, zonaId: undefined, puntos: 3, motivo: undefined },
    });
    expect(resultado).toMatchObject({ puntos: 3 });
  });

  it('crea presentismo con zona válida', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue({ id: 20, categoriaId: 10 });
    await crearPuntosPresentismo({ equipoId: 1, categoriaId: 10, zonaId: 20, puntos: 2, motivo: 'Tribunal' });
    expect(mockedPrisma.puntosPresentismo.create).toHaveBeenCalledWith({
      data: { equipoId: 1, categoriaId: 10, zonaId: 20, puntos: 2, motivo: 'Tribunal' },
    });
  });

  it('rechaza si el equipo no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);
    await expect(crearPuntosPresentismo({ equipoId: 999, categoriaId: 10, puntos: 3 })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.puntosPresentismo.create).not.toHaveBeenCalled();
  });

  it('rechaza si la categoría no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(crearPuntosPresentismo({ equipoId: 1, categoriaId: 999, puntos: 3 })).rejects.toThrow(NotFoundError);
  });

  it('rechaza si la zona no pertenece a la categoría', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue({ id: 20, categoriaId: 999 });
    await expect(crearPuntosPresentismo({ equipoId: 1, categoriaId: 10, zonaId: 20, puntos: 3 })).rejects.toThrow(
      ValidationError,
    );
  });
});

describe('eliminarPuntosPresentismo', () => {
  it('elimina el registro existente', async () => {
    mockedPrisma.puntosPresentismo.findUnique.mockResolvedValue({ id: 5, puntos: 3 });
    await eliminarPuntosPresentismo(5);
    expect(mockedPrisma.puntosPresentismo.delete).toHaveBeenCalledWith({ where: { id: 5 } });
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.puntosPresentismo.findUnique.mockResolvedValue(null);
    await expect(eliminarPuntosPresentismo(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.puntosPresentismo.delete).not.toHaveBeenCalled();
  });
});
