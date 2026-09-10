import { prisma } from '../src/lib/prisma';
import {
  actualizarZona,
  crearZona,
  eliminarZona,
  listarZonas,
  obtenerZona,
} from '../src/modules/zonas/zonas.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    categoria: { findUnique: jest.fn() },
    zona: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  categoria: { findUnique: jest.Mock };
  zona: { findMany: jest.Mock; findUnique: jest.Mock; create: jest.Mock; update: jest.Mock; delete: jest.Mock };
};

const zonaMock = { id: 20, categoriaId: 10, nombre: 'Zona 1' };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.zona.findUnique.mockResolvedValue(zonaMock);
  mockedPrisma.categoria.findUnique.mockResolvedValue({ id: 10 });
  mockedPrisma.zona.create.mockImplementation(({ data }) => Promise.resolve({ id: 20, ...data }));
});

describe('listarZonas / obtenerZona', () => {
  it('lista con filtro opcional', async () => {
    await listarZonas(10);
    expect(mockedPrisma.zona.findMany).toHaveBeenCalledWith({
      where: { categoriaId: 10 },
      orderBy: { nombre: 'asc' },
    });
  });

  it('obtiene por id', async () => {
    await expect(obtenerZona(20)).resolves.toEqual(zonaMock);
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue(null);
    await expect(obtenerZona(999)).rejects.toThrow(NotFoundError);
  });
});

describe('crearZona / actualizarZona / eliminarZona', () => {
  it('crea si la categoría existe', async () => {
    await expect(crearZona({ categoriaId: 10, nombre: 'Zona 1' })).resolves.toMatchObject({ categoriaId: 10 });
  });

  it('rechaza si la categoría no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(crearZona({ categoriaId: 999, nombre: 'Zona 1' })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.zona.create).not.toHaveBeenCalled();
  });

  it('actualiza y elimina tras verificar que existe', async () => {
    await actualizarZona(20, { nombre: 'Zona 2' });
    expect(mockedPrisma.zona.update).toHaveBeenCalledWith({ where: { id: 20 }, data: { nombre: 'Zona 2' } });
    await eliminarZona(20);
    expect(mockedPrisma.zona.delete).toHaveBeenCalledWith({ where: { id: 20 } });
  });

  it('no actualiza ni elimina si no existe', async () => {
    mockedPrisma.zona.findUnique.mockResolvedValue(null);
    await expect(actualizarZona(999, { nombre: 'X' })).rejects.toThrow(NotFoundError);
    await expect(eliminarZona(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.zona.update).not.toHaveBeenCalled();
    expect(mockedPrisma.zona.delete).not.toHaveBeenCalled();
  });
});
