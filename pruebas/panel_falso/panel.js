function doLogin(){ var p=document.getElementById('pwd').value;
  var x=new XMLHttpRequest(); x.open('POST','/api/login'); x.setRequestHeader('X-Test','1');
  x.send('user=admin&pass_hash='+md5hex(p)+'&password='+encodeURIComponent(p)); }
function md5hex(s){ return 'fakehash'; }
document.addEventListener('DOMContentLoaded',function(){
  document.getElementById('entrar').addEventListener('click',function(){ doLogin(); });
  document.getElementById('bloquear').addEventListener('click',function(){
    fetch('/api/block',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({mac:'aa:bb:cc:dd:ee:ff',block:true,psk:'MiClaveWifi123'})}); });
});
