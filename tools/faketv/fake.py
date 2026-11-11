import asyncio, ssl, json, base64, time
from aiohttp import web, WSMsgType
import os
N=0
MODE=os.environ.get('MODE','')
LOG=open('log.txt','w',buffering=1)
def log(*a): print(time.strftime('%H:%M:%S'),*a,file=LOG)
async def info(req):
    log('HTTP',req.method,req.path)
    return web.json_response({"device":{"name":"[TV] Fake","modelName":"UN55FAKE","wifiMac":"AA:BB:CC:00:11:22","PowerState":"on","type":"Samsung SmartTV"},"name":"[TV] Fake","isSupport":"{}"})
async def app_status(req):
    log('HTTP',req.method,req.path)
    return web.json_response({"id":req.match_info['id'],"name":"x","running":req.match_info['id']=="111299001912","visible":True})
async def ws(req):
    log('WS connect',req.url)
    w=web.WebSocketResponse(); await w.prepare(req)
    global N
    N+=1
    if MODE=='pending':
        await asyncio.sleep(3)
        if N<=2:
            await w.send_str(json.dumps({'event':'ms.channel.timeout'})); await w.close(); return w
    await asyncio.sleep(1.5)
    await w.send_str(json.dumps({"data":{"clients":[],"id":"x","token":"55667788"},"event":"ms.channel.connect"}))
    async def ime():
        await asyncio.sleep(12)
        await w.send_str(json.dumps({"event":"ms.remote.imeStart","data":base64.b64encode(b"").decode()}))
    asyncio.ensure_future(ime())
    async for m in w:
        if m.type==WSMsgType.TEXT:
            d=json.loads(m.data); log('RX',m.data)
            p=d.get('params',{})
            if p.get('event')=='ed.installedApp.get':
                await w.send_str(json.dumps({"data":{"data":[{"appId":"111299001912","app_type":2,"name":"YouTube"},{"appId":"11101200001","app_type":2,"name":"Netflix"},{"appId":"zzz","app_type":2,"name":"Meu App Extra"}]},"event":"ed.installedApp.get"}))
            if p.get('TypeOfRemote')=='SendInputString': log('TEXT=',base64.b64decode(p['Cmd']).decode())
    log('WS closed')
    return w
app=web.Application()
app.router.add_get('/api/v2/',info)
app.router.add_get('/api/v2/applications/{id}',app_status)
app.router.add_post('/api/v2/applications/{id}',app_status)
app.router.add_get('/api/v2/channels/samsung.remote.control',ws)
async def main():
    r=web.AppRunner(app); await r.setup()
    await web.TCPSite(r,'0.0.0.0',8001).start()
    sc=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); sc.load_cert_chain('c.pem','k.pem')
    await web.TCPSite(r,'0.0.0.0',8002,ssl_context=sc).start()
    await asyncio.sleep(3600)
asyncio.run(main())
