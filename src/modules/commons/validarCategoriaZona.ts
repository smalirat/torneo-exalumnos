import { prisma } from '../../lib/prisma';
import { NotFoundError, ValidationError } from '../../utils/AppError';

/**
 * Valida que la categoría exista y, si se pasa zonaId, que la zona exista
 * Y pertenezca a esa categoría. Se usa desde standings y partidos para no
 * duplicar esta lógica.
 */
export async function validarCategoriaYZona(categoriaId: number, zonaId?: number): Promise<void> {
  const categoria = await prisma.categoria.findUnique({ where: { id: categoriaId } });
  if (!categoria) {
    throw new NotFoundError('Categoria', categoriaId);
  }

  if (zonaId !== undefined) {
    const zona = await prisma.zona.findUnique({ where: { id: zonaId } });
    if (!zona) {
      throw new NotFoundError('Zona', zonaId);
    }
    if (zona.categoriaId !== categoriaId) {
      throw new ValidationError('La zona indicada no pertenece a la categoría indicada');
    }
  }
}
