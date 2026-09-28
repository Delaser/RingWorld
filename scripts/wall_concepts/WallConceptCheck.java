import java.util.*;
public class WallConceptCheck {
 public static void main(String[] args){
  for(int seed=0;seed<50;seed++){
   int[] original=new int[96*48];Random rng=new Random(seed);for(int i=0;i<original.length;i++)original[i]=rng.nextInt(100);
   int[] saved=original.clone(),hist=new int[5];for(int r:original)hist[WallConceptPatterns.bucket(r)]++;
   for(int c=0;c<5;c++){
    int[] result=WallConceptPatterns.generate(c,original,96,48,seed),other=WallConceptPatterns.generate(c,original,96,48,seed),actual=new int[5];
    if(!Arrays.equals(result,other)||!Arrays.equals(saved,original))throw new AssertionError("determinism/ownership");
    for(int r:result){if(r<0||r>99)throw new AssertionError("range");actual[WallConceptPatterns.bucket(r)]++;}
    if(!Arrays.equals(hist,actual))throw new AssertionError("palette coverage");
    if(c==0&&!Arrays.equals(saved,result))throw new AssertionError("baseline changed");
   }
  }
  System.out.println("PASS: 250 cases; deterministic, immutable input, valid rolls, exact palette histogram; baseline identity.");
  int[] input=new int[96*48];Random rng=new Random(8128);for(int i=0;i<input.length;i++)input[i]=rng.nextInt(100);
  for(int c=0;c<5;c++){
   long[] times=new long[101];int changed=0;
   for(int repeat=0;repeat<121;repeat++){
    long start=System.nanoTime();int[] result=WallConceptPatterns.generate(c,input,96,48,8128);
    long elapsed=System.nanoTime()-start;if(repeat>=20)times[repeat-20]=elapsed;
    if(repeat==120)for(int i=0;i<input.length;i++)if(WallConceptPatterns.bucket(input[i])!=WallConceptPatterns.bucket(result[i]))changed++;
   }
   Arrays.sort(times);System.out.printf(Locale.ROOT,"%s: median %.3f ms, p95 %.3f ms per 4608 cells; synthetic category changes %.1f%%\n",WallConceptPatterns.NAMES[c],times[50]/1e6,times[95]/1e6,changed*100.0/input.length);
  }
 }
}
