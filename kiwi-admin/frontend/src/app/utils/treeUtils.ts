import { NzTreeNodeOptions } from "ng-zorro-antd/tree";



export class TreeUtils {
   
   

    static convertToTreeNode(nodes: any[], idField: string = 'id', nameField: string = 'name', childrenField: string = 'children'): NzTreeNodeOptions[] {
        const newNodes: NzTreeNodeOptions[] = [];

        for (const item of nodes) {
            const node: NzTreeNodeOptions = {
                ...item,
                title: item[nameField],
                key: item[idField],
                children: item[childrenField] ? TreeUtils.convertToTreeNode(item[childrenField], idField, nameField, childrenField) : [],
                
            };

            newNodes.push(node);
        }

        return newNodes;
    }

     static buildMap(nodes: any[], idField : string = 'id'): Map<string, any> {
        const map = new Map<string, any>();
        for (const item of nodes) {
            map.set(item[idField], item);
            if (item.children && item.children.length > 0) {
                const childMap = TreeUtils.buildMap(item.children, idField);
                for (const [key, value] of childMap) {
                    map.set(key, value);
                }
            }
        }
        return map;
    }

    static forEach(nodes: any[], fn: (node: any) => void): NzTreeNodeOptions[] {
        const newNodes =[];
        for (const node of nodes) {
            let newNode = {...node};
            fn(newNode);
            if (node.children && node.children.length > 0) {
                newNode.children = TreeUtils.forEach(node.children, fn);
            }
            newNodes.push(newNode);
        }
        return newNodes;
    }
}