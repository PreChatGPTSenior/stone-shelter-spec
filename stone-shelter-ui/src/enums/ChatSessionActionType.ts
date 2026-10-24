export enum ChatSessionActionType {
  Begin = 'BEGIN',
  Delta = 'DELTA',
  Completed = 'COMPLETED',
  Failed = 'FAILED',
  RestoreStarted = 'RESTORE_STARTED',
  Restored = 'RESTORED',
  RestoreFailed = 'RESTORE_FAILED',
}
