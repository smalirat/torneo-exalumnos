// Jerarquía de errores de negocio. Los services SIEMPRE lanzan una de estas
// clases (nunca un string ni un Error genérico) para que el errorHandler
// pueda mapear a un status HTTP correcto sin adivinar.
export class AppError extends Error {
  constructor(
    message: string,
    public readonly statusCode: number,
    public readonly code: string,
  ) {
    super(message);
    this.name = this.constructor.name;
    Error.captureStackTrace(this, this.constructor);
  }
}

export class NotFoundError extends AppError {
  constructor(entidad: string, id?: number | string) {
    super(
      id !== undefined ? `${entidad} con id ${id} no encontrado` : `${entidad} no encontrado`,
      404,
      'NOT_FOUND',
    );
  }
}

export class ValidationError extends AppError {
  constructor(message: string, public readonly details?: unknown) {
    super(message, 400, 'VALIDATION_ERROR');
  }
}

export class ConflictError extends AppError {
  constructor(message: string) {
    super(message, 409, 'CONFLICT');
  }
}

export class UnauthorizedError extends AppError {
  constructor(message = 'No autenticado') {
    super(message, 401, 'UNAUTHORIZED');
  }
}

export class ForbiddenError extends AppError {
  constructor(message = 'No tenés permiso para esta acción') {
    super(message, 403, 'FORBIDDEN');
  }
}
