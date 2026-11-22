import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { extname, join } from 'node:path';
const root=new URL('./dist/',import.meta.url);
const types={'.html':'text/html; charset=utf-8','.js':'text/javascript; charset=utf-8','.css':'text/css; charset=utf-8'};
createServer(async(req,res)=>{try{const path=req.url==='/'?'index.html':req.url.split('?')[0].replace(/^\//,'');const file=new URL(path,root);if(!file.href.startsWith(root.href))throw new Error('invalid path');const data=await readFile(file);res.writeHead(200,{'Content-Type':types[extname(path)]||'application/octet-stream'});res.end(data)}catch{res.writeHead(404);res.end('Not found')}}).listen(8090,'127.0.0.1',()=>console.log('Preview em http://localhost:8090'));
