import java.util.Arrays;

/** Offline visual-study sampler. Not registered as a world-generation option. */
public final class WallConceptPatterns {
    public static final String[] NAMES = {"Original", "Repair history", "Service routes", "Staggered courses", "Weathered joints"};
    private static long hash(long s, int x, int y) {
        long v=s ^ x*0x9E3779B97F4A7C15L ^ y*0xC2B2AE3D27D4EB4FL;
        v=(v^(v>>>30))*0xBF58476D1CE4E5B9L;
        v=(v^(v>>>27))*0x94D049BB133111EBL;
        return v^(v>>>31);
    }
    private static int pick(long s,int x,int y,int n){return (int)Long.remainderUnsigned(hash(s,x,y),n);}
    public static int bucket(int r){return r<40?0:r<68?1:r<86?2:r<97?3:4;}
    public static int[] generate(int concept,int[] original,int width,int height,long seed) {
        if(concept<0||concept>=NAMES.length||original.length!=width*height)throw new IllegalArgumentException();
        int[] result=original.clone();
        if(concept==0)return result;
        boolean[] preferred=new boolean[result.length];
        for(int y=0;y<height;y++)for(int x=0;x<width;x++){
            int i=y*width+x;
            if(concept==1){
                // Sparse inset repair patches, with an occasional missing corner.
                int cx=x/20,cy=y/16,px=2+pick(seed,cx,cy,7),py=2+pick(seed+1,cx,cy,5);
                int w=4+pick(seed+2,cx,cy,6),h=4+pick(seed+3,cx,cy,5);
                int u=x%20-px,v=y%16-py;
                if(u>=0&&u<w&&v>=0&&v<h&&!(u<2&&v<2)){
                    int base=pick(seed+4,cx,cy,2)==0?25:55;
                    result[i]=pick(seed+5,x,y,12)==0?(base==25?55:25):base;preferred[i]=true;
                }
            }else if(concept==2){
                // Continuous narrow vertical trunk; short branches terminate within a bay.
                int bx=x/32,offset=7+pick(seed,bx,0,9),u=x%32;
                int junction=6+pick(seed+8,bx,y/20,8),v=y%20;
                boolean trunk=u>=offset&&u<offset+2;
                boolean branch=v==junction&&u>=offset&&u<=offset+9;
                if(trunk||branch){result[i]=55;preferred[i]=true;}
                if(v>=junction-1&&v<=junction+1&&u==offset+9){result[i]=90;preferred[i]=true;}
            }else if(concept==3){
                int bandStart=0,band=0;
                while(bandStart+7+pick(seed,band,0,7)<=y){bandStart+=7+pick(seed,band,0,7);band++;}
                int shift=pick(seed+9,band,0,width),sx=(x+shift)%width;
                result[i]=original[y*width+sx];
                // A broken course joint, not a full grid line.
                if(y==bandStart&&pick(seed+10,x/9,band,5)!=0){result[i]=90;preferred[i]=true;}
            }else{
                // Existing high-roll joints seed downward stains. No new regular grid.
                boolean trail=false;
                for(int up=1;up<=7&&y+up<height;up++){
                    int anchor=(y+up)*width+x;
                    if(original[anchor]>=86&&pick(seed,x/2,(y+up)/3,7)==0){trail=true;break;}
                }
                if(trail&&pick(seed+11,x,y,5)!=0){result[i]=55;preferred[i]=true;}
            }
        }
        // Match the baseline's exact five-material histogram. Keep authored
        // features where possible; compensate in unmarked areas deterministically.
        int[] wanted=new int[5],have=new int[5];
        for(int r:original)wanted[bucket(r)]++;
        for(int r:result)have[bucket(r)]++;
        Integer[] order=new Integer[result.length];for(int i=0;i<order.length;i++)order[i]=i;
        Arrays.sort(order,(a,b)->{
            int c=Boolean.compare(preferred[a],preferred[b]);
            return c!=0?c:Long.compareUnsigned(hash(seed+12,a,0),hash(seed+12,b,0));
        });
        int[] representative={25,55,76,90,98};
        for(int i:order){
            int from=bucket(result[i]);if(have[from]<=wanted[from])continue;
            int to=bucket(original[i]);
            if(have[to]>=wanted[to]){to=0;while(to<5&&have[to]>=wanted[to])to++;}
            if(to==5)break;
            result[i]=representative[to];have[from]--;have[to]++;
        }
        return result;
    }
}
