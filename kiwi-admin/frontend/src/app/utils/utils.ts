
export class Utils {
    


    static joinUrl(root:string, ...path: string[]) {

        let url = root;
        for(let i = 0; i < path.length; i++){
            if (url.endsWith("/")) {
                url = url.substring(0, url.length - 1);
            }
            let p = path[i];
            if(p.startsWith("/")){
                p = p.substring(1);
            }
            url = url + "/" + p;
        }
        return url;
    }





}
