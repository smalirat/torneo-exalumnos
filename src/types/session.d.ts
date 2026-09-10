import 'express-session';

// Extendemos SessionData para que req.session.userId/rol/equipoId
// tengan tipado en vez de ser `any`.
declare module 'express-session' {
  interface SessionData {
    userId?: number;
    username?: string;
    rol?: 'ADMIN' | 'DELEGADO';
    equipoId?: number | null;
  }
}
