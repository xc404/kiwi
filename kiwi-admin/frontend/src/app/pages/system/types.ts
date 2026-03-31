
export interface Role {
    id: number;
    code: string;
    name: string;
    menuIds: string[];
    permissions?: string[];
}

export interface Permission{
    key:string;
    description:string;
}