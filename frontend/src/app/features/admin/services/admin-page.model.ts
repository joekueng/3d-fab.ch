import { Observable, of, switchMap } from 'rxjs';
import type { PageInfo } from '../../../shared/models/page-info';

export interface AdminPage<T> extends PageInfo {
  items: T[];
}

export function loadAdminPage<T>(
  fetchPage: (index: number) => Observable<AdminPage<T>>,
  index: number,
): Observable<AdminPage<T>> {
  return fetchPage(index).pipe(
    switchMap((result) => {
      const lastPage = Math.max(
        0,
        Math.ceil(result.totalElements / result.size) - 1,
      );
      return index > lastPage ? loadAdminPage(fetchPage, lastPage) : of(result);
    }),
  );
}
