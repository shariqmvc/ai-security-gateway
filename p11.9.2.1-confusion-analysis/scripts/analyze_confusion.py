import json
from collections import Counter, defaultdict
from pathlib import Path
ROOT=Path(__file__).resolve().parents[1]
BASELINE=ROOT.parent/'p11.9.2-semantic-retrieval'/'results'/'retrieval_baseline.json'
OUT=ROOT/'results'; OUT.mkdir(exist_ok=True)
ATTACKS={'PROMPT_INJECTION','JAILBREAK','SYSTEM_PROMPT_EXTRACTION','DATA_EXFILTRATION','INDIRECT_INJECTION','TOOL_MANIPULATION','RAG_POISONING','OBFUSCATED_ATTACK','MULTILINGUAL_ATTACK'}
BENIGN={'BENIGN','HARD_BENIGN'}
def f1(p,r): return 0 if p+r==0 else 2*p*r/(p+r)
def main():
 d=json.loads(BASELINE.read_text(encoding='utf-8')); rows=d['results']
 labels=sorted(set(r['expected'] for r in rows)|set(r['predicted'] for r in rows if r['predicted']))
 matrix={e:{p:0 for p in labels} for e in labels}
 for r in rows:
  p=r['predicted'] or 'NONE'; matrix[r['expected']][p]=matrix[r['expected']].get(p,0)+1
 metrics={}
 for l in labels:
  tp=sum(r['expected']==l and r['predicted']==l for r in rows); fp=sum(r['expected']!=l and r['predicted']==l for r in rows); fn=sum(r['expected']==l and r['predicted']!=l for r in rows)
  p=tp/(tp+fp) if tp+fp else 0; rec=tp/(tp+fn) if tp+fn else 0
  metrics[l]={'support':sum(r['expected']==l for r in rows),'precision':p,'recall':rec,'f1':f1(p,rec)}
 attacks=[r for r in rows if r['expected'] in ATTACKS]; benign=[r for r in rows if r['expected'] in BENIGN]
 ar=sum(r['predicted'] in ATTACKS for r in attacks); bfp=sum(r['predicted'] in ATTACKS for r in benign)
 cross=Counter((r['expected'],r['predicted']) for r in rows if r['predicted']!=r['expected'])
 fp=[r for r in benign if r['predicted'] in ATTACKS]; fn=[r for r in attacks if r['predicted'] in BENIGN]; acc=[r for r in attacks if r['predicted'] in ATTACKS and r['predicted']!=r['expected']]
 sim=defaultdict(list)
 for r in rows: sim[r['expected']].append(r['similarity'])
 payload={'examples':len(rows),'confusionMatrix':matrix,'perClass':metrics,'attackVsBenign':{'attackRecall':ar/len(attacks),'benignFalsePositiveRate':bfp/len(benign),'attackCount':len(attacks),'benignCount':len(benign)},'crossConfusions':[{'expected':e,'predicted':p,'count':n} for (e,p),n in cross.most_common()],'falsePositiveBenign':fp,'falseNegativeAttacks':fn,'attackCrossClass':acc,'similarityByExpected':{k:{'count':len(v),'min':min(v),'max':max(v),'mean':sum(v)/len(v)} for k,v in sim.items()}}
 (OUT/'confusion_matrix.json').write_text(json.dumps(payload,indent=2),encoding='utf-8')
 lines=['P11.9.2.1 SEMANTIC RETRIEVAL CONFUSION ANALYSIS','='*58,f'Examples: {len(rows)}','','TOP-1 CONFUSION MATRIX','-'*58]
 for e in sorted(matrix): lines.append(f'{e}: {matrix[e]}')
 lines += ['','PER-CLASS METRICS','-'*58]
 for l,m in sorted(metrics.items()): lines.append(f"{l:24s} support={m['support']:3d} P={m['precision']:.4f} R={m['recall']:.4f} F1={m['f1']:.4f}")
 lines += ['','ATTACK-vs-BENIGN','-'*58,f"Attack recall: {ar/len(attacks):.4f}",f"Benign false-positive rate: {bfp/len(benign):.4f}",'','CROSS-CLASS CONFUSIONS','-'*58]
 for (e,p),n in cross.most_common(): lines.append(f'{e} -> {p}: {n}')
 lines += ['','BENIGN FALSE POSITIVES','-'*58]
 for r in fp: lines.append(f"{r['id']} | expected={r['expected']} predicted={r['predicted']} similarity={r['similarity']:.4f}")
 lines += ['','ATTACKS MISREAD AS BENIGN','-'*58]
 for r in fn: lines.append(f"{r['id']} | expected={r['expected']} predicted={r['predicted']} similarity={r['similarity']:.4f}")
 lines += ['','ATTACK CROSS-CLASS ERRORS','-'*58]
 for r in acc: lines.append(f"{r['id']} | expected={r['expected']} predicted={r['predicted']} similarity={r['similarity']:.4f}")
 (OUT/'confusion_report.txt').write_text('\n'.join(lines),encoding='utf-8')
 print('\n'.join(lines[:45])); print(f'\nSaved: {OUT/"confusion_report.txt"}'); print(f'Saved: {OUT/"confusion_matrix.json"}')
if __name__=='__main__': main()
