import { NextFunction, Request, Response } from 'express';
import { Prisma } from '@prisma/client';
import { ZodError } from 'zod';
import { AppError } from '../utils/AppError';

// Traduce errores de Prisma a nuestros AppError, así el resto del código
// no necesita saber qué código de error tira Prisma en cada caso.
function mapPrismaError(err: Prisma.PrismaClientKnownRequestError): AppError {
  switch (err.code) {
    case 'P2002': // unique constraint violation
      return new AppError(
        `Ya existe un registro con esos datos (campo: ${(err.meta?.target as string[])?.join(', ')})`,
        409,
        'UNIQUE_CONSTRAINT',
      );
    case 'P2025': // record not found (update/delete)
      return new AppError('El registro no existe', 404, 'NOT_FOUND');
    case 'P2003': // foreign key constraint
      return new AppError('Referencia inválida a otro registro (foreign key)', 400, 'FOREIGN_KEY');
    default:
      return new AppError('Error de base de datos', 500, 'DB_ERROR');
  }
}

// eslint-disable-next-line @typescript-eslint/no-unused-vars
export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction) {
  let appError: AppError;

  if (err instanceof AppError) {
    appError = err;
  } else if (err instanceof ZodError) {
    appError = new AppError('Datos inválidos', 400, 'VALIDATION_ERROR');
    res.status(appError.statusCode).json({
      error: { code: appError.code, message: appError.message, details: err.flatten() },
    });
    return;
  } else if (err instanceof Prisma.PrismaClientKnownRequestError) {
    appError = mapPrismaError(err);
  } else {
    appError = new AppError('Error interno del servidor', 500, 'INTERNAL_ERROR');
  }

  if (appError.statusCode >= 500) {
    // Los 500 sí queremos verlos completos en los logs del server
    console.error(err);
  }

  res.status(appError.statusCode).json({
    error: { code: appError.code, message: appError.message },
  });
}
