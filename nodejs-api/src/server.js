import app from './app.js';
import logger from './utils/logger.js';

const PORT    = process.env.PORT    ?? 3000;
const JAR_PATH = process.env.JAR_PATH ?? '(not set — using default path)';

app.listen(PORT, () => {
  logger.info(`Node.js API listening on port ${PORT}`);
  logger.info(`Java JAR path: ${JAR_PATH}`);
});
