import { computed, signal, WritableSignal } from "@angular/core";


export interface TreeNode {
    id: string | number;
    children?: TreeNode[];
    _level: number
    _expanded: boolean;
    rowData?: any;
    _parent?: TreeNode;
    _checked?: boolean;
}



export class TreeModel {

    items: WritableSignal<TreeNode[]>;

    expandedItems = signal(new Set());

    flatternItems: WritableSignal<TreeNode[]>;

    constructor(items: TreeNode[]) {
        this.items = signal(items);
        this.flatternItems = signal(this.getFlatternItems());
    }

    setItems(items: TreeNode[]) {
        this.items.set(items);
        this.flatternItems.set(this.getFlatternItems());
    }

    toggleCollapse(id: string | number) {
        this.expandedItems.update(set => {
            if (set.has(id)) {
                set.delete(id);
            } else {
                set.add(id);
            }
            return set;
        })
    }

    getFlatternItems() {
        let nodes: TreeNode[] = [];
        for (let item of this.items()) {
            nodes.push(...this.flatItem(item));
        }
        return nodes;
    };

    isExpanded(id: string | number): boolean {
        return this.expandedItems().has(id);
    }

    flatItem(root: TreeNode): TreeNode[] {
        const stack: TreeNode[] = [];
        const array: TreeNode[] = [];
        stack.push({ ...root, _level: 0, _expanded: true, rowData: root });
        while (stack.length !== 0) {
            const node = stack.pop()!;
            array.push(node);
            if (node.children) {
                for (let i = node.children.length - 1; i >= 0; i--) {
                    stack.push({ ...node.children[i], _level: node._level! + 1, _expanded: false, _parent: node, rowData: node.children[i] });
                }
            }
        }
        return array;
    }
}