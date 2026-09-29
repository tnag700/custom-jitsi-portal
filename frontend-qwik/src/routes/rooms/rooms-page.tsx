import { component$, useSignal } from "@qwik.dev/core";
import { useLocation } from "@qwik.dev/router";
import { RoomList } from "~/lib/domains/rooms";
import { PageNavigation } from "~/lib/shared/components/PageNavigation";
import {
  RoomManagement,
  type RoomManagementRequest,
} from "./components/RoomManagement";
import { useRooms } from "./route-handlers";

export default component$(() => {
  const roomsData = useRooms();
  const location = useLocation();
  const request = useSignal<RoomManagementRequest | null>(null);

  return (
    <div>
      <RoomList
        rooms={roomsData.value.content}
        totalElements={roomsData.value.totalElements}
        onEdit$={(room) => {
          request.value = { kind: "edit", room };
        }}
        onClose$={(room) => {
          request.value = { kind: "close", room };
        }}
        onDelete$={(room) => {
          request.value = { kind: "delete", room };
        }}
        onCreateClick$={() => {
          request.value = { kind: "create", room: null };
        }}
      />
      <PageNavigation
        currentUrl={location.url.href}
        parameter="roomsPage"
        page={roomsData.value.page}
        totalPages={roomsData.value.totalPages}
        label="Страницы комнат"
      />
      <RoomManagement request={request} />
    </div>
  );
});
