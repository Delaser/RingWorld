package dev.ringworld.world;

/** Deterministic, bounded geometry for the Industrial wall elements. */
public final class RingIndustrialElements {
    public static final int UNCHANGED = -999;
    public static final int AIR = -1;
    public static final int KIND_COUNT = 12;
    public static final int HALF_WIDTH = 18;
    public static final int MAX_RELIEF = 3;
    private RingIndustrialElements() { }

    public record Feature(int kind, int centerX, int offsetX) { }

    public static boolean enabled(RingWallStyle style) {
        return style.palette() == RingWallStyle.Palette.INDUSTRIAL
                && style.pattern() == RingWallStyle.Pattern.ENGINEERED;
    }

    private static long mix(long value) {
        value = (value ^ (value >>> 30)) * 0xBF58476D1CE4E5B9L;
        value = (value ^ (value >>> 27)) * 0x94D049BB133111EBL;
        return value ^ (value >>> 31);
    }

    /** Approximately one element per 160 blocks; cells close exactly around the ring. */
    public static Feature feature(int x, int circumference, long seed, int side) {
        if (circumference < 64 || side < 0 || side > 1) throw new IllegalArgumentException();
        int canonical = Math.floorMod(x, circumference);
        int count = Math.max(1, circumference / 160);
        int cell = (int)((long)canonical * count / circumference);
        int start = (int)(((long)cell * circumference + count - 1) / count);
        int end = (int)(((long)(cell + 1) * circumference + count - 1) / count);
        long key = mix(seed ^ (long)side * 0x632BE59BD9B4E019L ^ cell * 0x9E3779B97F4A7C15L);
        int center = start + 24 + (int)Long.remainderUnsigned(key, end - start - 48);
        // Coprime strides permute all twelve motifs within each group.
        long group = mix(seed ^ side * 7919L ^ (cell / KIND_COUNT) * 0xD1B54A32D192ED03L);
        int stride = switch ((int)(group & 3)) { case 0 -> 1; case 1 -> 5; case 2 -> 7; default -> 11; };
        int kind = ((cell % KIND_COUNT) * stride + (int)Long.remainderUnsigned(group >>> 2, KIND_COUNT)) % KIND_COUNT;
        return new Feature(kind, center, canonical - center);
    }

    /** Below 24 exposed blocks, leave the wall plain rather than crush the motifs. */
    public static int roll(Feature feature, int yAboveBase, int exposedHeight,
                           int towardInterior, int thickness) {
        if (exposedHeight < 24 || thickness < 2 || yAboveBase < 0
                || yAboveBase >= Math.min(33, exposedHeight)
                || Math.abs(feature.offsetX()) > HALF_WIDTH
                || towardInterior < -Math.min(3, thickness - 1) || towardInterior > MAX_RELIEF) {
            return UNCHANGED;
        }
        int height = Math.min(33, exposedHeight);
        int first = yAboveBase * 33 / height;
        int last = ((yAboveBase + 1) * 33 / height) - 1;
        int result = UNCHANGED;
        for (int y = first; y <= last; y++) {
            int next = prototypeRoll(feature.kind(), feature.offsetX() + 32, y, towardInterior);
            if (next != UNCHANGED && (next >= 0 || result == UNCHANGED)) result = next;
        }
        // Keep at least the outermost block intact, even on a two-block-thick wall.
        if (result == AIR && towardInterior == -(thickness - 1)) return 55;
        return result;
    }

 private static int prototypeRoll(int kind,int x,int y,int z){
  int u=x-32;
  switch(kind){
   case 0:{ // 6-wide tapering support, sunk one block below the waterline.
    int projection=y<12?3:y<22?2:1;
    if(Math.abs(u)<=2&&y<31&&z>=0&&z<=projection)return Math.abs(u)==2?76:25;
    break;
   }
   case 1:{
    if(Math.abs(u)<=1&&z>=-3&&z<=0)return z==-3?55:-1;
    if(Math.abs(u)==2&&z==0)return 76;
    break;
   }
   case 2:{
    if(Math.abs(u)<=2&&y>=1&&y<=5&&z>=-3&&z<=0)return z==-3?55:-1;
    if(Math.abs(u)<=4&&y>=0&&y<=7&&z==0)return 90;
    break;
   }
   case 3:{
    if(Math.abs(u)<=7&&y>=14&&y<=20&&z>=-2&&z<=0){
     if(z==-2)return 55;
     return y%3==2?76:-1;
    }
    if(Math.abs(u)<=9&&y>=12&&y<=22&&z==0)return 25;
    break;
   }
   case 4:{
    if(Math.abs(u)<=18&&y==9&&z>=0&&z<=3)return 25;
    if(Math.abs(u)<=18&&y==11&&z==3)return 76;
    if(Math.abs(u)<=18&&u%9==0&&y==10&&z==3)return 76;
    if(Math.abs(u)<=2&&y>=10&&y<=15&&z==0)return Math.abs(u)==2||y==15?90:55;
    break;
   }
   case 5:{
    if(Math.abs(u)<=1&&y>=1&&y<=4&&z==2)return 55;
    if(Math.abs(u)<=3&&y==24&&z==2)return 76;
    if(Math.abs(u)<=4&&y>=0&&y<29&&z>=0&&z<=2)return Math.abs(u)==4?76:25;
    break;
   }
   case 6:{
    if(Math.abs(u)<=10&&y>=8&&y<=24){
     int inset=Math.abs(y-16)/3;
     if(Math.abs(u)<=9-inset&&z>=-3&&z<=0){
      if(z==-3)return 55;
      if(z==-2&&(u%5==0||y==13||y==19))return u%5==0?76:98;
      return -1;
     }
     if(z==0)return 25;
    }
    break;
   }
   case 7:{ // Wide twin machinery bays with a solid central divider.
    if(Math.abs(u)<=15&&y>=9&&y<=23){
     if(Math.abs(u)>=2&&Math.abs(u)<=13&&y>=11&&y<=21&&z>=-3&&z<=0){
      if(z==-3)return 55;
      if(z==-2&&(Math.abs(u)%4==0||y==14||y==18))return y==14||y==18?98:76;
      return AIR;
     }
     if(z==0)return Math.abs(u)<=1?76:25;
    }
    break;
   }
   case 8:{ // Tall machinery pocket with staggered horizontal service bars.
    if(Math.abs(u)<=6&&y>=2&&y<=29){
     if(Math.abs(u)<=4&&y>=4&&y<=27&&z>=-3&&z<=0){
      if(z==-3)return 55;
      if(z==-2&&(u==0||y%6==0))return u==0?76:98;
      return AIR;
     }
     if(z==0)return 25;
    }
    break;
   }
   case 9: case 10: case 11:{ // Embedded routes: straight, stepped and paired.
    int center=kind==10?(y<11?-4:y<22?0:4):0;
    int distance=kind==11?Math.abs(Math.abs(u)-5):Math.abs(u-center);
    if(distance<=1&&y>=1&&y<=31&&z>=-2&&z<=0){
     if(z==-2)return 55;
     if(z==-1&&distance==0)return y%8==4?98:76;
     return AIR;
    }
    // Horizontal links join the stepped route at each change of direction.
    if(kind==10&&(y==10||y==21)&&u>=(y==10?-4:0)&&u<=(y==10?0:4)&&z>=-2&&z<=0)
     return z==-2?55:z==-1?76:AIR;
    if(distance==2&&y>=1&&y<=31&&z==0)return 25;
    break;
   }
   default:throw new IllegalArgumentException();
  }
  return UNCHANGED;
 }
}
