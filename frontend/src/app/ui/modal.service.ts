import { ApplicationRef, ComponentRef, EmbeddedViewRef, Injectable, Injector, Type, ViewContainerRef } from '@angular/core';
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
  private hostVc: ViewContainerRef | null = null;

  open<T>(component: Type<T>, props?: Partial<T>): string {
    const id = `modal-${++this.counter}`;
    this.instances.set(id, { id, componentRef: null });
    this.opened$.next(this.instances.get(id) ?? null);
    return id;
  }

  registerHost(vc: ViewContainerRef): void {
    this.hostVc = vc;
  }

  async openComponent<T, R = void>(component: Type<T>, props?: Partial<T>): Promise<R | null> {
    if (!this.hostVc) {
      throw new Error('Modal host not registered');
    }
    const id = this.open(component, props);
    // create component in host
    const compRef = this.hostVc.createComponent(component as any);
    // assign props
    if (props) {
      Object.assign((compRef.instance as any), props as any);
    }
    this.attachRef(id, compRef as ComponentRef<any>);

    return new Promise<R | null>(resolve => {
      // listen for host being cleared to resolve with null
      const sub = this.opened$.subscribe(inst => {
        if (!inst) {
          sub.unsubscribe();
          resolve(null);
        }
      });

      // subscribe to common event emitters on the component instance
      const names = ['close', 'closed', 'select', 'dismiss', 'result'];
      for (const name of names) {
        const maybe = (compRef.instance as any)?.[name];
        if (maybe && typeof maybe.subscribe === 'function') {
          const evsub = maybe.subscribe((value: R) => {
            try { evsub.unsubscribe(); } catch {}
            this.close(id);
            resolve(value);
          });
          break;
        }
      }
    });
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
