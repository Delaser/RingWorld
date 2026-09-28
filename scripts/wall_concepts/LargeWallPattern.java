/** Authored large-scale visual prototype, not a production generation option. */
public final class LargeWallPattern {
 public static boolean gate(int x,int y){return x>=164&&x<220&&y<64&&y>=0&&!(y>56&&(x<170||x>=214));}
 public static boolean hatch(int x,int y){return x>=302&&x<316&&y>=8&&y<28;}
 public static boolean recess(int x,int y,int d){return d<2&&(gate(x,y)||hatch(x,y));}
 public static boolean light(int x,int y,int d){return d==0&&((x==160||x==223)&&(y==16||y==48)||((x==300||x==317)&&y==28));}
 public static int roll(int x,int y,int original){
  int[] bounds={0,72,152,232,304,384};int bay=0;while(bay<4&&x>=bounds[bay+1])bay++;
  int u=x-bounds[bay],w=bounds[bay+1]-bounds[bay];
  if(gate(x,y))return x==191||x==192?76:y%16==0?25:55;
  if(hatch(x,y))return x==308||x==309?76:55;
  if((x>=160&&x<224&&y<68)||(x>=299&&x<319&&y>=5&&y<31))return 90;
  if(u<3||u>=w-3)return 76;
  if(y>=80&&y<84)return 25;
  int joint=bay%2==0?38:52;
  if(y==joint||y==joint+1)return 76;
  // Original detail is reserved for narrow joins; panel interiors remain quiet.
  if(u<6||u>=w-6||Math.abs(y-joint)<4||y>86)return original;
  return (bay+y/48)%3==0?55:25;
 }
}
