export class AppError extends Error {
  constructor(
    message: string,
    public statusCode = 502,
  ) {
    super(message);
    this.name = 'AppError';
  }
}

export class ProviderError extends AppError {
  constructor(message: string, statusCode = 502) {
    super(message, statusCode);
    this.name = 'ProviderError';
  }
}

export class NotFoundError extends AppError {
  constructor(message = 'Not found') {
    super(message, 404);
    this.name = 'NotFoundError';
  }
}

export class ConfigError extends AppError {
  constructor(message: string) {
    super(message, 501);
    this.name = 'ConfigError';
  }
}
