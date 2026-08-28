import bcrypt from 'bcrypt';
import { prisma } from '../../lib/prisma';
import { ConflictError, NotFoundError } from '../../utils/AppError';
import { CrearUsuarioInput } from './usuarios.validation';

const SALT_ROUNDS = 10;

// Select explícito para nunca devolver passwordHash en una respuesta HTTP.
const SELECT_SEGURO = { id: true, username: true, rol: true, equipoId: true, createdAt: true } as const;

export async function listarUsuarios() {
  return prisma.usuario.findMany({ select: SELECT_SEGURO, orderBy: { username: 'asc' } });
}

export async function crearUsuario(input: CrearUsuarioInput) {
  const existente = await prisma.usuario.findUnique({ where: { username: input.username } });
  if (existente) {
    throw new ConflictError(`Ya existe un usuario con username "${input.username}"`);
  }

  if (input.equipoId !== undefined) {
    const equipo = await prisma.equipo.findUnique({ where: { id: input.equipoId } });
    if (!equipo) throw new NotFoundError('Equipo', input.equipoId);
  }

  const passwordHash = await bcrypt.hash(input.password, SALT_ROUNDS);

  return prisma.usuario.create({
    data: { username: input.username, passwordHash, rol: input.rol, equipoId: input.equipoId },
    select: SELECT_SEGURO,
  });
}

export async function eliminarUsuario(id: number) {
  const usuario = await prisma.usuario.findUnique({ where: { id } });
  if (!usuario) throw new NotFoundError('Usuario', id);
  await prisma.usuario.delete({ where: { id } });
}
