/** Authored elements fitted to this test world's measured 33-block exposed wall. */
public final class ScaledWallElements2 {
 public static final int[] ORIGINS={4160,4352,4416,4480,4544,4608,4672,4736};
 public static final String[] NAMES={"Buttress","Expansion joint","Drainage outlet","Ventilation bank","Maintenance gallery","Service shaft","Exposed machinery","Braced breach"};
 // z is depth towards the viewer from the original inner face: 0 = Z-122.
 // -2 is recessed; +3 protrudes. -999 means preserve the existing block.
 public static int roll(int kind,int x,int y,int z){
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
   case 7:{
    if(Math.abs(u)<=9&&y>=0&&y<24){
     int radius=y<17?6:Math.max(1,6-(y-17));
     if(Math.abs(u)<radius&&z>=-3&&z<=0)return z==-3?55:-1;
     if(z==0&&(Math.abs(u)==radius||Math.abs(u)==radius+1))return 25;
    }
    // Surface braces flank and cross the former opening.
    if(y>=2&&y<=23&&z==1&&(Math.abs(u)==8||Math.abs(u-(y-12)/2)<=1||Math.abs(u+(y-12)/2)<=1))return 76;
    break;
   }
   default:throw new IllegalArgumentException();
  }
  return -999;
 }
}
