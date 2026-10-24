import { ApiError } from './ApiError';

export class StoneReservationError extends ApiError {
  constructor(status: number, message: string) {
    super(status, message);
    this.name = 'StoneReservationError';
  }
}
