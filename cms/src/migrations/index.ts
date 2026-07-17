import * as migration_20260704_073338_initial from './20260704_073338_initial';

export const migrations = [
  {
    up: migration_20260704_073338_initial.up,
    down: migration_20260704_073338_initial.down,
    name: '20260704_073338_initial'
  },
];
