import bcrypt from 'bcrypt';
import { prisma } from '../../src/lib/prisma';
import { crearUsuario } from '../../src/modules/usuarios/usuarios.service';
import { crearUsuarioSchema } from '../../src/modules/usuarios/usuarios.validation';
import { ConflictError, NotFoundError } from '../../src/utils/AppError';

jest.mock('../../src/lib/prisma', () => ({
  prisma: {
    usuario: { findUnique: jest.fn(), create: jest.fn() },
    equipo: { findUnique: jest.fn() },
  },
}));
jest.mock('bcrypt');

const mockedPrisma = prisma as unknown as {
  usuario: { findUnique: jest.Mock; create: jest.Mock };
  equipo: { findUnique: jest.Mock };
};
const mockedBcrypt = bcrypt as unknown as { hash: jest.Mock };

beforeEach(() => {
  jest.clearAllMocks();
  mockedPrisma.usuario.findUnique.mockResolvedValue(null);
  mockedBcrypt.hash.mockResolvedValue('hash-simulado');
  mockedPrisma.usuario.create.mockImplementation(({ data }) =>
    Promise.resolve({ id: 1, username: data.username, rol: data.rol, equipoId: data.equipoId ?? null }),
  );
});

describe('crearUsuario', () => {
  it('crea un admin sin equipoId', async () => {
    const resultado = await crearUsuario({ username: 'admin2', password: 'password123', rol: 'ADMIN' as never });
    expect(resultado.username).toBe('admin2');
    expect(mockedBcrypt.hash).toHaveBeenCalledWith('password123', 10);
  });

  it('crea un delegado con equipoId válido', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue({ id: 5, nombre: 'GULP' });

    const resultado = await crearUsuario({
      username: 'delegado_gulp',
      password: 'password123',
      rol: 'DELEGADO' as never,
      equipoId: 5,
    });

    expect(resultado.equipoId).toBe(5);
  });

  it('rechaza si el username ya existe', async () => {
    mockedPrisma.usuario.findUnique.mockResolvedValue({ id: 1, username: 'admin' });

    await expect(
      crearUsuario({ username: 'admin', password: 'password123', rol: 'ADMIN' as never }),
    ).rejects.toThrow(ConflictError);
    expect(mockedPrisma.usuario.create).not.toHaveBeenCalled();
  });

  it('rechaza si el equipoId del delegado no existe', async () => {
    mockedPrisma.equipo.findUnique.mockResolvedValue(null);

    await expect(
      crearUsuario({ username: 'delegado_x', password: 'password123', rol: 'DELEGADO' as never, equipoId: 999 }),
    ).rejects.toThrow(NotFoundError);
  });
});

describe('crearUsuarioSchema (validación)', () => {
  it('rechaza un DELEGADO sin equipoId', () => {
    const resultado = crearUsuarioSchema.safeParse({
      username: 'delegado_sin_equipo',
      password: 'password123',
      rol: 'DELEGADO',
    });
    expect(resultado.success).toBe(false);
  });

  it('acepta un ADMIN sin equipoId', () => {
    const resultado = crearUsuarioSchema.safeParse({
      username: 'nuevo_admin',
      password: 'password123',
      rol: 'ADMIN',
    });
    expect(resultado.success).toBe(true);
  });

  it('rechaza una password de menos de 8 caracteres', () => {
    const resultado = crearUsuarioSchema.safeParse({
      username: 'x',
      password: '123',
      rol: 'ADMIN',
    });
    expect(resultado.success).toBe(false);
  });
});
