import bcrypt from 'bcrypt';
import { Usuario } from '@prisma/client';
import { prisma } from '../../lib/prisma';
import { UnauthorizedError } from '../../utils/AppError';
import { LoginInput } from './auth.validation';

/**
 * Devuelve el usuario si username/password son correctos.
 * Mensaje de error deliberadamente genérico ("usuario o contraseña
 * incorrectos") para no revelar si el username existe o no.
 */
export async function login(input: LoginInput): Promise<Usuario> {
  const usuario = await prisma.usuario.findUnique({ where: { username: input.username } });
  if (!usuario) {
    throw new UnauthorizedError('Usuario o contraseña incorrectos');
  }

  const passwordValida = await bcrypt.compare(input.password, usuario.passwordHash);
  if (!passwordValida) {
    throw new UnauthorizedError('Usuario o contraseña incorrectos');
  }

  return usuario;
}
