<?php
declare(strict_types=1);
require_once dirname(__DIR__).'/includes/bootstrap.php';
require_admin();
ensure_v8_schema($pdo);
$pageTitle='คำสั่งซื้อ';

$q=trim((string)($_GET['q']??''));
$status=trim((string)($_GET['status']??''));
$where=[]; $params=[];
if($q!==''){
    if(ctype_digit($q)){
        $where[]='(o.order_id=? OR o.listing_id=? OR o.tracking_number LIKE ? OR o.title_snapshot LIKE ? OR buyer.username LIKE ? OR seller.username LIKE ?)';
        $params[]=(int)$q; $params[]=(int)$q; $params[]="%$q%"; $params[]="%$q%"; $params[]="%$q%"; $params[]="%$q%";
    } else {
        $where[]='(o.tracking_number LIKE ? OR o.title_snapshot LIKE ? OR buyer.username LIKE ? OR seller.username LIKE ? OR o.recipient_name LIKE ? OR o.phone LIKE ?)';
        for($i=0;$i<6;$i++) $params[]="%$q%";
    }
}
$allowed=['pending_confirmation','preparing','shipped','completed','cancelled'];
if(in_array($status,$allowed,true)){ $where[]='o.status=?'; $params[]=$status; }

$sql="SELECT o.*,buyer.username buyer_username,seller.username seller_username
    FROM orders o
    JOIN users buyer ON buyer.id=o.buyer_id
    JOIN users seller ON seller.id=o.seller_id".
    ($where?' WHERE '.implode(' AND ',$where):'').
    " ORDER BY o.order_id DESC LIMIT 500";
$st=$pdo->prepare($sql); $st->execute($params); $rows=$st->fetchAll();

include '_header.php';
?>
<form method="get" class="card" style="margin-bottom:16px">
  <input name="q" value="<?=e($q)?>" placeholder="Order ID / สินค้า / ผู้ซื้อ / ผู้ขาย / พัสดุ" style="padding:9px;width:min(95%,420px)">
  <select name="status" style="padding:9px">
    <option value="">ทุกสถานะ</option>
    <?php foreach($allowed as $s):?><option value="<?=$s?>" <?=$status===$s?'selected':''?>><?=e(order_status_label($s))?></option><?php endforeach;?>
  </select>
  <button>ค้นหา</button>
</form>

<table>
<tr><th>Order</th><th>สินค้า</th><th>ผู้ซื้อ → ผู้ขาย</th><th>ราคา / วันที่</th><th>สถานะ</th><th>รายละเอียด</th></tr>
<?php foreach($rows as $r):?>
<tr>
<td><strong>#<?=$r['order_id']?></strong><br><span class="muted">Listing #<?=$r['listing_id']?></span></td>
<td><?=e($r['title_snapshot'])?></td>
<td><?=e($r['buyer_username'])?> → <?=e($r['seller_username'])?></td>
<td><?=number_format((float)$r['price_snapshot'],2)?> บาท<br><span class="muted"><?=e($r['created_at'])?></span></td>
<td><strong><?=e(order_status_label($r['status']))?></strong>
<?php if($r['tracking_number']):?><br>พัสดุ: <?=e($r['tracking_number'])?><?php endif;?></td>
<td>
<details>
<summary>ดู Order</summary>
<p><strong>ผู้รับ:</strong> <?=e($r['recipient_name'])?> / <?=e($r['phone'])?></p>
<p><strong>ที่อยู่:</strong>
<?=e($r['house_no_moo'])?>
<?=e($r['soi']?'ซอย '.$r['soi']:'')?>
<?=e($r['road']?'ถนน '.$r['road']:'')?>
<?=e($r['subdistrict'])?> <?=e($r['district'])?> <?=e($r['province'])?> <?=e($r['postal_code'])?>
</p>
<?php if($r['note']):?><p><strong>หมายเหตุ:</strong> <?=nl2br(e($r['note']))?></p><?php endif;?>
<p class="muted">อัปเดตล่าสุด <?=e($r['updated_at'])?></p>
</details>
</td>
</tr>
<?php endforeach;?>
</table>
<?php include '_footer.php';?>
