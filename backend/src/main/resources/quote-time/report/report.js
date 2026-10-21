'use strict';
const data=JSON.parse(document.getElementById('data').textContent);
const $=id=>document.getElementById(id);
const qualityNames={standard:'Standard',draft:'Bozza',extra_fine:'Alta definizione'};
const fmt=new Intl.NumberFormat('it-CH',{maximumFractionDigits:2});
const percent=value=>(value>0?'+':'')+fmt.format(value)+'%';
const duration=seconds=>{const h=Math.floor(seconds/3600),m=Math.floor(seconds%3600/60),s=seconds%60;return (h?h+'h ':'')+(m?m+'m ':'')+(!h?s+'s':'');};
const escape=value=>String(value).replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const errorClass=value=>Math.abs(value)>10?'error-high':Math.abs(value)>5?'error-mid':'';
let sortKey=null,sortDirection=1;
$('configs').textContent=data.rows.length;
$('pairs').textContent=fmt.format(data.comparisons);
$('median').textContent=fmt.format(data.validationMedianErrorPercent)+'%';
$('worst').textContent=fmt.format(data.validationMaxErrorPercent)+'%';
$('scope').textContent='4 materiali · 4 ugelli · pezzi rappresentativi · OrcaSlicer 2.3.2 · nessun effetto sui preventivi';
$('linux-note').textContent=data.linuxComparisons ? data.linuxComparisons+' confronti Linux disponibili per questi pezzi.' : 'Questi sei nuovi pezzi non sono ancora stati confrontati su Linux. I precedenti controlli Linux sui pezzi piccoli non sono riutilizzati come verifica di questi dati.';
$('version').textContent=data.version+' · '+data.sourceRuntime+' · '+data.createdAt;
const preferred=['PLA','PLA TOUGH','PETG','TPU','PC'];
const materials=[...new Set(data.rows.map(r=>r.material))].sort((a,b)=>preferred.indexOf(a)-preferred.indexOf(b));
for(const material of materials)$('material').add(new Option(material,material));
for(const nozzle of [...new Set(data.rows.map(r=>r.nozzle))].sort((a,b)=>a-b))$('nozzle').add(new Option(fmt.format(nozzle)+' mm',nozzle));
for(const model of data.models)$('model').add(new Option(model.label+' · '+(model.group==='validation'?'verifica':'calibrazione'),model.id));
$('model').value='cube65';
const selectedSample=r=>{const s=r.sample;const a1=$('phase').value==='model'?s.a1ModelSeconds:s.a1Seconds,p2s=$('phase').value==='model'?s.p2sModelSeconds:s.p2sSeconds;return {...s,a1Seconds:a1,p2sSeconds:p2s,ratio:a1/p2s,nativeDifferencePercent:100*(p2s/a1-1)};};
function selectedRows(){
 const quality=$('quality').value,material=$('material').value,nozzle=$('nozzle').value,model=$('model').value;
 return data.rows.filter(r=>(quality==='all'||r.qualities.includes(quality))&&(material==='all'||r.material===material)&&(nozzle==='all'||r.nozzle===Number(nozzle))&&(!$('outliers').checked||r.validationMaxErrorPercent>5))
 .map(row=>({...row,sample:row.samples.find(s=>s.model===model)})).filter(r=>r.sample);
}
function render(){
 let rows=selectedRows();
 const value=r=>{const s=selectedSample(r);return ({a1:s.a1Seconds,p2s:s.p2sSeconds,delta:s.nativeDifferencePercent,error:Math.abs(s.errorPercent)})[sortKey];};
 if(sortKey)rows.sort((a,b)=>(value(a)-value(b))*sortDirection);
 else rows.sort((a,b)=>preferred.indexOf(a.material)-preferred.indexOf(b.material)||a.nozzle-b.nozzle||a.layer-b.layer||a.qualities.join().localeCompare(b.qualities.join()));
 $('count').textContent=rows.length+' configurazioni · '+data.models.find(m=>m.id===$('model').value).label;
 $('details').querySelector('tbody').innerHTML=rows.map(r=>{
  const s=selectedSample(r);
  const profileTitle='A1: '+r.a1Process+' | '+r.a1Filament+'\nP2S: '+r.p2sProcess+' | '+r.p2sFilament;
  const linux=s.linux?'<span class="platform-ok" title="'+escape('Linux A1 '+duration(s.linux.a1Seconds)+' · P2S '+duration(s.linux.p2sSeconds)+' · scarto normalizzato '+percent(s.linux.errorPercent))+'">'+fmt.format(s.linux.platformDifferencePercent)+'%</span>':'<span class="muted">Non misurato</span>';
  return '<tr><td>'+escape(r.material)+'</td><td>'+fmt.format(r.nozzle)+' / '+fmt.format(r.layer)+' mm</td><td title="'+escape(profileTitle)+'">'+r.qualities.map(q=>escape(qualityNames[q])).join(' / ')+'</td><td>'+duration(s.a1Seconds)+'</td><td>'+duration(s.p2sSeconds)+'</td><td>'+percent(s.nativeDifferencePercent)+'</td><td>'+fmt.format(s.ratio)+'×</td><td>'+duration(s.a1PreparationSeconds)+'</td><td>'+duration(s.p2sPreparationSeconds)+'</td><td>'+duration(s.normalizedSeconds)+'</td><td class="'+errorClass(s.errorPercent)+'">'+percent(s.errorPercent)+'</td><td class="'+errorClass(r.validationMaxErrorPercent)+'">'+fmt.format(r.validationMaxErrorPercent)+'%</td><td>'+linux+'</td></tr>';
 }).join('');
 renderHeatmap(rows);
}
function renderHeatmap(rows){
 if($('quality').value==='all'){$('heatmap').innerHTML='<p class="muted">Seleziona una qualità per la matrice. La tabella mostra tutte le configurazioni.</p>';return;}
 const columns=[...new Set(rows.map(r=>r.nozzle+'/'+r.layer))].sort((a,b)=>{const aa=a.split('/').map(Number),bb=b.split('/').map(Number);return aa[0]-bb[0]||aa[1]-bb[1];});
 const shownMaterials=materials.filter(m=>rows.some(r=>r.material===m));
 let html='<table aria-label="Rapporto A1/P2S per materiale, ugello e strato"><thead><tr><th>Materiale<br>Ugello / strato</th>'+columns.map(c=>'<th>'+c.split('/').map(n=>fmt.format(Number(n))).join('<br>')+' mm</th>').join('')+'</tr></thead><tbody>';
 for(const material of shownMaterials){
  html+='<tr><td>'+escape(material)+'</td>';
  for(const column of columns){
   const r=rows.find(r=>r.material===material&&r.nozzle+'/'+r.layer===column);
   if(!r){html+='<td><span class="blank">—</span></td>';continue;}
   const s=selectedSample(r),amount=Math.max(0,Math.min(1,(s.ratio-1)/2.5)),color=s.ratio<.98?'#dcece9':'hsl(49 92% '+(97-amount*48)+'%)';
   const title='A1 '+duration(s.a1Seconds)+' · P2S '+duration(s.p2sSeconds)+' · '+percent(s.nativeDifferencePercent)+'\n'+r.a1Process+' / '+r.p2sProcess;
   html+='<td><span style="background:'+color+'" title="'+escape(title)+'">'+fmt.format(s.ratio)+'×</span></td>';
  }
  html+='</tr>';
 }
 $('heatmap').innerHTML=html+'</tbody></table>';
}
for(const id of ['model','quality','material','nozzle','phase','outliers'])$(id).addEventListener('change',render);
for(const button of document.querySelectorAll('[data-sort]'))button.addEventListener('click',()=>{sortDirection=sortKey===button.dataset.sort?-sortDirection:1;sortKey=button.dataset.sort;render();});
$('export').addEventListener('click',()=>{
 const rows=[['Materiale','Ugello mm','Strato mm','Qualità','Pezzo','Tipo tempo','A1 s','P2S s','Delta P2S %','A1/P2S','Preparazione A1 G-code s','Preparazione P2S G-code s','Curva sperimentale totale s','Scarto %','Scarto massimo verifica %'],...selectedRows().map(r=>{const s=selectedSample(r);return [r.material,r.nozzle,r.layer,r.qualities.map(q=>qualityNames[q]).join(' / '),s.model,$('phase').value,s.a1Seconds,s.p2sSeconds,s.nativeDifferencePercent,s.ratio,s.a1PreparationSeconds,s.p2sPreparationSeconds,s.normalizedSeconds,s.errorPercent,r.validationMaxErrorPercent];})];
 const csv='\uFEFF'+rows.map(row=>row.map(v=>'"'+String(v).replace(/"/g,'""')+'"').join(';')).join('\r\n');
 const url=URL.createObjectURL(new Blob([csv],{type:'text/csv;charset=utf-8'}));const a=document.createElement('a');a.href=url;a.download='a1-p2s-'+$('model').value+'.csv';a.click();setTimeout(()=>URL.revokeObjectURL(url),1000);
});
render();
