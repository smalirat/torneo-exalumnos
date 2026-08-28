import { config } from 'dotenv';
import path from 'path';

// Cargamos .env.test si existe (DB de test separada de la de desarrollo);
// si no existe, dotenv no falla, simplemente no sobreescribe nada.
config({ path: path.resolve(__dirname, '../.env.test') });

// Fallback para que jest no explote si todavía no configuraste .env.test
process.env.SESSION_SECRET ||= 'test-secret-not-for-production';
process.env.DATABASE_URL ||= process.env.DATABASE_URL_TEST || '';
