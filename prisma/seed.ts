import bcrypt from 'bcrypt';
import { PrismaClient, RolUsuario } from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  const username = process.env.SEED_ADMIN_USERNAME ?? 'admin';
  const password = process.env.SEED_ADMIN_PASSWORD ?? 'changeme123';

  const existente = await prisma.usuario.findUnique({ where: { username } });
  if (existente) {
    console.log(`Usuario "${username}" ya existe, no se crea de nuevo.`);
    return;
  }

  const passwordHash = await bcrypt.hash(password, 10);
  await prisma.usuario.create({
    data: { username, passwordHash, rol: RolUsuario.ADMIN },
  });

  console.log(`✅ Usuario admin creado: username="${username}" password="${password}"`);
  console.log('⚠️  Cambiá esta contraseña antes de ir a producción.');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });
