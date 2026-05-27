/**
 * Wire-format types for the {@code /api/table/*} endpoints.
 *
 * <p>Local TS interfaces rather than OpenAPI-generated ones: the SPA is on
 * the path to GraphQL, so REST client codegen is throwaway. Type drift is
 * guarded by {@code TableViewServiceContractTest} pinning the record shapes
 * server-side.
 */

export type TableCellType = 'STRING' | 'INTEGER' | 'LONG' | 'DOUBLE' | 'DATE' | 'BOOLEAN';

export interface TableColumnDescriptor {
  id?: string;
  label?: string;
  type?: TableCellType;
}

export interface TableRow {
  id?: string;
  cells?: Record<string, unknown>;
}

export interface TablePage {
  columns?: TableColumnDescriptor[];
  rows?: TableRow[];
  totalCount?: number;
  nextCursor?: string;
  incomplete?: boolean;
}
