import bcrypt from 'bcrypt';
import { 
  PrismaClient, 
  RolUsuario, 
  NombreTorneo, 
  NombreCategoria 
} from '@prisma/client';

const prisma = new PrismaClient();

async function main() {
  // =========================================================
  // 1. USUARIO ADMIN
  // =========================================================
  const username = process.env.SEED_ADMIN_USERNAME ?? 'admin';
  const password = process.env.SEED_ADMIN_PASSWORD ?? 'changeme123';

  const existente = await prisma.usuario.findUnique({ where: { username } });
  
  if (!existente) {
    const passwordHash = await bcrypt.hash(password, 10);
    await prisma.usuario.create({
      data: { username, passwordHash, rol: RolUsuario.ADMIN },
    });
    console.log(`✅ Usuario admin creado: username="${username}" password="${password}"`);
    console.log('⚠️  Cambiá esta contraseña antes de ir a producción.');
  } else {
    console.log(`✅ Usuario "${username}" ya existe, no se crea de nuevo.`);
  }

  // =========================================================
  // 2. TEMPORADA
  // =========================================================
  const temporada2026 = await prisma.temporada.upsert({
    where: { anio: 2026 },
    update: {},
    create: { anio: 2026 },
  });
  console.log('✅ Temporada 2026 lista.');

  // =========================================================
  // 3. TORNEOS (Apertura y Clausura)
  // =========================================================
  const apertura = await prisma.torneo.upsert({
    where: { 
      temporadaId_nombre: { temporadaId: temporada2026.id, nombre: NombreTorneo.APERTURA } 
    },
    update: {},
    create: { temporadaId: temporada2026.id, nombre: NombreTorneo.APERTURA },
  });

  const clausura = await prisma.torneo.upsert({
    where: { 
      temporadaId_nombre: { temporadaId: temporada2026.id, nombre: NombreTorneo.CLAUSURA } 
    },
    update: {},
    create: { temporadaId: temporada2026.id, nombre: NombreTorneo.CLAUSURA },
  });
  console.log('✅ Torneos Apertura y Clausura listos.');

  // =========================================================
  // 4. ESTRUCTURA APERTURA (Cat A con Zona 1 y 2)
  // =========================================================
  const catAApertura = await prisma.categoria.upsert({
    where: { 
      torneoId_nombre: { torneoId: apertura.id, nombre: NombreCategoria.A } 
    },
    update: {},
    create: { torneoId: apertura.id, nombre: NombreCategoria.A },
  });

  await prisma.zona.upsert({
    where: { 
      categoriaId_nombre: { categoriaId: catAApertura.id, nombre: 'Zona 1' } 
    },
    update: {},
    create: { categoriaId: catAApertura.id, nombre: 'Zona 1' },
  });

  await prisma.zona.upsert({
    where: { 
      categoriaId_nombre: { categoriaId: catAApertura.id, nombre: 'Zona 2' } 
    },
    update: {},
    create: { categoriaId: catAApertura.id, nombre: 'Zona 2' },
  });
  console.log('✅ Categoría A (Zona 1 y Zona 2) creada en el Apertura.');

  // =========================================================
  // 5. ESTRUCTURA CLAUSURA (Cat A y B sin zonas)
  // =========================================================
  await prisma.categoria.upsert({
    where: { 
      torneoId_nombre: { torneoId: clausura.id, nombre: NombreCategoria.A } 
    },
    update: {},
    create: { torneoId: clausura.id, nombre: NombreCategoria.A },
  });

  await prisma.categoria.upsert({
    where: { 
      torneoId_nombre: { torneoId: clausura.id, nombre: NombreCategoria.B } 
    },
    update: {},
    create: { torneoId: clausura.id, nombre: NombreCategoria.B },
  });
  console.log('✅ Categorías A y B (sin zonas) creadas en el Clausura.');
}

main()
  .catch((e) => {
    console.error(e);
    process.exit(1);
  })
  .finally(async () => {
    await prisma.$disconnect();
  });