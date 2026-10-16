-- Following tournaments and in-app notifications were removed from the product.
drop table follows;
drop table notifications;
alter table users drop column notifications_seen_at;
