import { ApplicationRef, ComponentRef, EmbeddedViewRef, Injectable, Injector, Type } from '@angular/core';
import { Subject } from 'rxjs';

export interface ModalInstance {
  id: string;
  componentRef: ComponentRef<any> | null;
}

@Injectable({ providedIn: 'root' })
export class ModalService {
  private instances = new Map<string, ModalInstance>();
  private counter = 0;
  readonly opened$ = new Subject<ModalInstance | null>();

  open<T>(component: Type<T>, props?: Partial<T>): string {
    const id = `modal-${++this.counter}`;
    this.instances.set(id, { id, componentRef: null });
    this.opened$.next(this.instances.get(id) ?? null);
    return id;
  }

  attachRef(id: string, ref: ComponentRef<any>): void {
    const inst = this.instances.get(id);
    if (inst) {
      inst.componentRef = ref;
      this.opened$.next(inst);
    }
  }

  close(id: string | null): void {
    if (!id) return;
    const inst = this.instances.get(id);
    if (inst) {
      try {
        inst.componentRef?.destroy();
      } catch (e) {
        // ignore
      }
      this.instances.delete(id);
      this.opened$.next(null);
    }
  }

  closeAll(): void {
    for (const id of Array.from(this.instances.keys())) {
      this.close(id);
    }
  }
}
