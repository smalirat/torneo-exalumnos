import { prisma } from '../src/lib/prisma';
import {
  actualizarCategoria,
  crearCategoria,
  eliminarCategoria,
  listarCategorias,
  obtenerCategoria,
} from '../src/modules/categorias/categorias.service';
import { NotFoundError } from '../src/utils/AppError';

jest.mock('../src/lib/prisma', () => ({
  prisma: {
    torneo: { findUnique: jest.fn() },
    categoria: { findMany: jest.fn(), findUnique: jest.fn(), create: jest.fn(), update: jest.fn(), delete: jest.fn() },
  },
}));

const mockedPrisma = prisma as unknown as {
  torneo: { findUnique: jest.Mock };
  categoria: { findMany: jest.Mock; findUnique: jest.Mock; create: jest.Mock; update: jest.Mock; delete: jest.Mock };
};

const categoriaMock = { id: 10, torneoId: 1, nombre: 'A', zonas: [] };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.categoria.findUnique.mockResolvedValue(categoriaMock);
  mockedPrisma.torneo.findUnique.mockResolvedValue({ id: 1 });
  mockedPrisma.categoria.create.mockImplementation(({ data }) => Promise.resolve({ id: 10, ...data }));
});

describe('listarCategorias / obtenerCategoria', () => {
  it('lista con filtro opcional incluyendo zonas', async () => {
    await listarCategorias(1);
    expect(mockedPrisma.categoria.findMany).toHaveBeenCalledWith({
      where: { torneoId: 1 },
      include: { zonas: true },
      orderBy: { nombre: 'asc' },
    });
  });

  it('obtiene por id', async () => {
    await expect(obtenerCategoria(10)).resolves.toEqual(categoriaMock);
  });

  it('lanza NotFoundError si no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(obtenerCategoria(999)).rejects.toThrow(NotFoundError);
  });
});

describe('crearCategoria / actualizarCategoria / eliminarCategoria', () => {
  it('crea si el torneo existe', async () => {
    await expect(crearCategoria({ torneoId: 1, nombre: 'A' })).resolves.toMatchObject({ torneoId: 1 });
    expect(mockedPrisma.categoria.create).toHaveBeenCalled();
  });

  it('rechaza si el torneo no existe', async () => {
    mockedPrisma.torneo.findUnique.mockResolvedValue(null);
    await expect(crearCategoria({ torneoId: 999, nombre: 'A' })).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.categoria.create).not.toHaveBeenCalled();
  });

  it('actualiza tras verificar que existe', async () => {
    mockedPrisma.categoria.update.mockResolvedValue({ ...categoriaMock, nombre: 'B' });
    await actualizarCategoria(10, { nombre: 'B' });
    expect(mockedPrisma.categoria.update).toHaveBeenCalledWith({ where: { id: 10 }, data: { nombre: 'B' } });
  });

  it('no actualiza ni elimina si no existe', async () => {
    mockedPrisma.categoria.findUnique.mockResolvedValue(null);
    await expect(actualizarCategoria(999, { nombre: 'B' })).rejects.toThrow(NotFoundError);
    await expect(eliminarCategoria(999)).rejects.toThrow(NotFoundError);
    expect(mockedPrisma.categoria.update).not.toHaveBeenCalled();
    expect(mockedPrisma.categoria.delete).not.toHaveBeenCalled();
  });

  it('elimina tras verificar que existe', async () => {
    await eliminarCategoria(10);
    expect(mockedPrisma.categoria.delete).toHaveBeenCalledWith({ where: { id: 10 } });
  });
});
