import bcrypt from 'bcrypt';
import { prisma } from '../../src/lib/prisma';
import { login } from '../../src/modules/auth/auth.service';
import { UnauthorizedError } from '../../src/utils/AppError';

jest.mock('../../src/lib/prisma', () => ({
  prisma: { usuario: { findUnique: jest.fn() } },
}));
jest.mock('bcrypt');

const mockedPrisma = prisma as unknown as { usuario: { findUnique: jest.Mock } };
const mockedBcrypt = bcrypt as unknown as { compare: jest.Mock };

const usuarioMock = {
  id: 1,
  username: 'admin',
  passwordHash: 'hash-falso',
  rol: 'ADMIN',
  equipoId: null,
  createdAt: new Date(),
};

beforeEach(() => {
  jest.clearAllMocks();
});

describe('login', () => {
  it('devuelve el usuario si username y password son correctos', async () => {
    mockedPrisma.usuario.findUnique.mockResolvedValue(usuarioMock);
    mockedBcrypt.compare.mockResolvedValue(true);

    const resultado = await login({ username: 'admin', password: 'correcta' });

    expect(resultado).toEqual(usuarioMock);
    expect(mockedBcrypt.compare).toHaveBeenCalledWith('correcta', 'hash-falso');
  });

  it('rechaza si el username no existe', async () => {
    mockedPrisma.usuario.findUnique.mockResolvedValue(null);

    await expect(login({ username: 'no-existe', password: 'x' })).rejects.toThrow(UnauthorizedError);
    // Nunca debería intentar comparar un hash que no existe
    expect(mockedBcrypt.compare).not.toHaveBeenCalled();
  });

  it('rechaza si la password es incorrecta', async () => {
    mockedPrisma.usuario.findUnique.mockResolvedValue(usuarioMock);
    mockedBcrypt.compare.mockResolvedValue(false);

    await expect(login({ username: 'admin', password: 'incorrecta' })).rejects.toThrow(UnauthorizedError);
  });
});
