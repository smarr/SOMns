#!/bin/bash -eux

echo ""
echo "Clean Up Image"
echo ""
apt-get clean -y
apt-get autoclean -y

find /var/lib/apt -type f | xargs rm -f
find /var/lib/doc -type f | xargs rm -f
find /var/cache -type f | xargs rm -f

rm -rf /usr/src/vboxguest*
rm -rf /usr/share/doc

rm -rf /usr/src/linux-headers*
rm -rf /usr/share/locale/{af,an,am,ar,ary,as,ast,az,bal,be,bg,bn,bn_IN,bo,br,bs,byn,ca,ca@valencia,ckb,cr,crh,cs,csb,cv,cy,da,de,de_AT,dv,dz,el,en_AU,en_CA,en_GB,eo,es,et,et_EE,eu,fa,fa_AF,fi,fil,fo,fr,frp,fur,fy,ga,gd,gez,gl,gu,gv,haw,he,hi,hr,ht,hu,hy,id,is,it,ja,jv,ka,kk,km,kn,ko,kok,ku,ky,lb,lg,ln,lt,lo,lv,mg,mhr,mi,mk,ml,mn,mr,ms,mt,my,nb,nds,ne,nl,nn,no,nso,oc,or,os,pa,pam,pl,ps,pt,pt_BR,qu,ro,ru,rw,sc,sd,shn,si,sk,sl,so,sq,sr,sr*latin,sv,sw,ta,te,th,ti,tig,tk,tl,tr,trv,tt,ug,uk,ur,urd,uz,ve,vec,vi,wa,wal,wo,xh,zh,zh_HK,zh_CN,zh_TW,zu}

echo ""
echo "Clean Up Done"
echo ""
