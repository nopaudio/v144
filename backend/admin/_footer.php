
</main>
<nav class="mobile-bottom" aria-label="เมนูผู้ดูแลบนมือถือ">
  <a href="index.php"><span class="ico">⌂</span><span>ภาพรวม</span></a>
  <a href="index.php#pending"><span class="ico">▣</span><span>งานรอ</span></a>
  <a href="admin_alerts.php"><span class="ico">●</span><?php if(($headerUnread??0)>0):?><span class="badge-count"><?=($headerUnread??0)>99?'99+':($headerUnread??0)?></span><?php endif;?><span>แจ้งเตือน</span></a>
  <a href="users.php"><span class="ico">♙</span><span>สมาชิก</span></a>
  <a href="#" id="mobileMore"><span class="ico">☰</span><span>เพิ่มเติม</span></a>
</nav>
<script>
(function(){
  const toggle=document.getElementById('adminNavToggle');
  const links=document.getElementById('adminNavLinks');
  if(toggle&&links){
    toggle.addEventListener('click',()=>{
      const open=links.classList.toggle('open');
      toggle.setAttribute('aria-expanded',open?'true':'false');
    });
  }
  const more=document.getElementById('mobileMore');
  if(more&&links&&toggle){
    more.addEventListener('click',(e)=>{
      e.preventDefault();
      links.classList.add('open');
      toggle.setAttribute('aria-expanded','true');
      window.scrollTo({top:0,behavior:'smooth'});
    });
  }

  // Turn every existing Admin table into labelled cards on small screens
  // without rewriting each V9 page or changing its desktop table markup.
  document.querySelectorAll('table').forEach(table=>{
    const rows=Array.from(table.querySelectorAll('tr'));
    const headerRow=rows.find(row=>row.querySelector('th'));
    if(!headerRow)return;
    headerRow.classList.add('table-head-row');
    const labels=Array.from(headerRow.querySelectorAll('th')).map(th=>th.textContent.trim());
    rows.forEach(row=>{
      if(row===headerRow)return;
      Array.from(row.children).forEach((cell,index)=>{
        if(cell.tagName==='TD'&&!cell.hasAttribute('colspan')){
          cell.dataset.label=labels[index]||'ข้อมูล';
        }
      });
    });
  });

  // Keep V9 button text/behavior, only make the intent visually obvious.
  document.querySelectorAll('button,.btn').forEach(el=>{
    const text=(el.textContent||'').trim();
    if(/อนุมัติ|เปิดใช้|ปิดเรื่อง/.test(text))el.classList.add('approve');
    if(/ปฏิเสธ|ไม่ผ่าน|ระงับ|ลบ/.test(text))el.classList.add('danger');
    if(/รอตรวจ|กำลังตรวจ/.test(text))el.classList.add('warning');
  });

  const current=(location.pathname.split('/').pop()||'index.php');
  document.querySelectorAll('.nav-links a,.mobile-bottom a').forEach(a=>{
    const target=(a.getAttribute('href')||'').split('?')[0].split('#')[0];
    if(target===current)a.classList.add('current');
  });
})();
</script>
</body>
</html>
